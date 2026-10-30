package com.apiratelimiter.gateway.filter;

import com.apiratelimiter.gateway.model.ApiKeyPolicyInfo;
import com.apiratelimiter.gateway.service.GatewayLogService;
import com.apiratelimiter.gateway.service.KeyResolutionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DistributedRateLimiterGatewayFilterFactory
    extends AbstractGatewayFilterFactory<DistributedRateLimiterGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(DistributedRateLimiterGatewayFilterFactory.class);

    private final KeyResolutionService keyResolutionService;
    private final GatewayLogService logService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> fixedWindowScript;
    private final RedisScript<List<Long>> slidingWindowScript;
    private final RedisScript<List<Long>> tokenBucketScript;
    private final RedisScript<List<Long>> leakyBucketScript;
    private final MeterRegistry meterRegistry;

    @Value("${ratelimiter.fail-open:false}")
    private boolean failOpen;

    @Autowired
    public DistributedRateLimiterGatewayFilterFactory(
        KeyResolutionService keyResolutionService,
        GatewayLogService logService,
        ReactiveStringRedisTemplate redisTemplate,
        @Qualifier("fixedWindowScript") RedisScript<List<Long>> fixedWindowScript,
        @Qualifier("slidingWindowScript") RedisScript<List<Long>> slidingWindowScript,
        @Qualifier("tokenBucketScript") RedisScript<List<Long>> tokenBucketScript,
        @Qualifier("leakyBucketScript") RedisScript<List<Long>> leakyBucketScript,
        @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        super(Config.class);
        this.keyResolutionService = keyResolutionService;
        this.logService = logService;
        this.redisTemplate = redisTemplate;
        this.fixedWindowScript = fixedWindowScript;
        this.slidingWindowScript = slidingWindowScript;
        this.tokenBucketScript = tokenBucketScript;
        this.leakyBucketScript = leakyBucketScript;
        this.meterRegistry = meterRegistry;
    }

    public DistributedRateLimiterGatewayFilterFactory(
        KeyResolutionService keyResolutionService,
        GatewayLogService logService,
        ReactiveStringRedisTemplate redisTemplate,
        RedisScript<List<Long>> fixedWindowScript,
        RedisScript<List<Long>> slidingWindowScript,
        RedisScript<List<Long>> tokenBucketScript,
        RedisScript<List<Long>> leakyBucketScript
    ) {
        this(keyResolutionService, logService, redisTemplate, fixedWindowScript, slidingWindowScript, tokenBucketScript, leakyBucketScript, null);
    }

    public static class Config {
        // Filter configuration properties if needed
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            long startTime = System.currentTimeMillis();
            ServerHttpRequest request = exchange.getRequest();
            String correlationId = request.getHeaders().getFirst("X-Correlation-ID");
            if (!StringUtils.hasText(correlationId)) {
                correlationId = UUID.randomUUID().toString();
            }
            final String finalCorrelationId = correlationId;

            // Extract API key
            String rawApiKey = extractApiKey(request);
            if (!StringUtils.hasText(rawApiKey)) {
                log.warn("[{}] Missing API key for request {}", finalCorrelationId, request.getPath());
                return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Missing API Key", "Provide a valid API key via X-API-Key or Authorization: Bearer header", finalCorrelationId);
            }

            String keyHash = hashKey(rawApiKey);
            String clientIp = resolveClientIp(request);
            String endpoint = request.getPath().value();
            String method = request.getMethod().name();

            return keyResolutionService.resolveKey(keyHash)
                .flatMap(policyInfo -> {
                    if (!policyInfo.valid()) {
                        String reason = policyInfo.reason() != null ? policyInfo.reason() : "Invalid API key";
                        boolean isRevokedOrExpired = reason.toLowerCase().contains("revoked")
                                || reason.toLowerCase().contains("expired")
                                || reason.toLowerCase().contains("inactive");

                        HttpStatus status = isRevokedOrExpired ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
                        String error = isRevokedOrExpired ? "Invalid API Key" : "Unauthorized";

                        log.warn("[{}] Rejected request on {}: {} (HTTP {})", finalCorrelationId, request.getPath(), reason, status.value());
                        return writeErrorResponse(exchange, status, error, reason, finalCorrelationId);
                    }

                    // Execute Redis Lua rate limit evaluation
                    return executeRateLimit(keyHash, policyInfo)
                        .flatMap(result -> {
                            long responseTimeMs = System.currentTimeMillis() - startTime;
                            String prefix = policyInfo.keyPrefix() != null ? policyInfo.keyPrefix() : "arl_***";

                            if (result.allowed()) {
                                // Request permitted
                                ServerHttpResponse response = exchange.getResponse();
                                response.getHeaders().set("X-Correlation-ID", finalCorrelationId);
                                response.getHeaders().set("X-RateLimit-Limit", String.valueOf(result.limit()));
                                response.getHeaders().set("X-RateLimit-Remaining", String.valueOf(result.remaining()));
                                response.getHeaders().set("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));
                                response.getHeaders().set("X-RateLimit-Algorithm", result.algorithm());

                                logService.queueLog(prefix, endpoint, method, clientIp, 200, true, result.limit(), result.remaining(), result.algorithm(), policyInfo.policyName(), responseTimeMs);

                                recordMetrics("allowed", result.algorithm(), policyInfo.policyName(), 200, responseTimeMs);

                                return chain.filter(exchange);
                            } else {
                                // Rate limit exceeded (HTTP 429)
                                log.info("[{}] Throttled request on {} for policy {}. Retry after {}s",
                                    finalCorrelationId, endpoint, policyInfo.policyName(), result.retryAfterSeconds());

                                ServerHttpResponse response = exchange.getResponse();
                                response.getHeaders().set("X-Correlation-ID", finalCorrelationId);
                                response.getHeaders().set("X-RateLimit-Limit", String.valueOf(result.limit()));
                                response.getHeaders().set("X-RateLimit-Remaining", "0");
                                response.getHeaders().set("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));
                                response.getHeaders().set("X-RateLimit-Algorithm", result.algorithm());
                                response.getHeaders().set("Retry-After", String.valueOf(result.retryAfterSeconds()));

                                logService.queueLog(prefix, endpoint, method, clientIp, 429, false, result.limit(), 0L, result.algorithm(), policyInfo.policyName(), responseTimeMs);

                                recordMetrics("rejected", result.algorithm(), policyInfo.policyName(), 429, responseTimeMs);

                                return write429Response(exchange, policyInfo, result, finalCorrelationId);
                            }
                        })
                        .onErrorResume(e -> {
                            log.error("[{}] Error evaluating rate limit in Redis: {}", finalCorrelationId, e.getMessage());
                            if (failOpen) {
                                log.warn("[{}] Fail-open mode active. Allowing request through.", finalCorrelationId);
                                exchange.getResponse().getHeaders().set("X-RateLimit-Fallback", "FAIL_OPEN");
                                return chain.filter(exchange);
                            } else {
                                return writeErrorResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Rate Limiter Unavailable", "Distributed rate limiting state store unavailable. Fail-closed enforced.", finalCorrelationId);
                            }
                        });
                });
        };
    }

    private record EvaluationResult(
        boolean allowed,
        long limit,
        long remaining,
        long resetSeconds,
        long retryAfterSeconds,
        String algorithm
    ) {}

    private final ConcurrentHashMap<String, AtomicLong> localFixedWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> localSlidingWindows = new ConcurrentHashMap<>();
    private static class LocalBucket {
        double tokens;
        long lastRefillMs;
        LocalBucket(double tokens, long lastRefillMs) { this.tokens = tokens; this.lastRefillMs = lastRefillMs; }
    }
    private final ConcurrentHashMap<String, LocalBucket> localTokenBuckets = new ConcurrentHashMap<>();
    private static class LocalLeakyBucket {
        double water;
        long lastLeakMs;
        LocalLeakyBucket(double water, long lastLeakMs) { this.water = water; this.lastLeakMs = lastLeakMs; }
    }
    private final ConcurrentHashMap<String, LocalLeakyBucket> localLeakyBuckets = new ConcurrentHashMap<>();

    private Mono<EvaluationResult> executeRateLimit(String keyHash, ApiKeyPolicyInfo policy) {
        String algo = policy.algorithm() != null ? policy.algorithm().toUpperCase() : "TOKEN_BUCKET";
        long limit = policy.limitValue() != null ? policy.limitValue() : 60L;
        long windowSec = policy.timeWindowSeconds() != null ? policy.timeWindowSeconds() : 60L;
        long capacity = policy.burstCapacity() != null && policy.burstCapacity() > 0 ? policy.burstCapacity() : limit;
        double refillRate = policy.refillRate() != null && policy.refillRate() > 0 ? policy.refillRate() : (double) limit / windowSec;
        long nowMs = Instant.now().toEpochMilli();

        if (redisTemplate == null) {
            return Mono.just(evaluateInMemory(keyHash, algo, limit, windowSec, capacity, refillRate, nowMs));
        }

        Mono<EvaluationResult> redisMono;
        switch (algo) {
            case "FIXED_WINDOW": {
                long bucket = nowMs / (windowSec * 1000L);
                String redisKey = "rl:fw:" + keyHash + ":" + bucket;
                redisMono = redisTemplate.execute(
                    fixedWindowScript,
                    List.of(redisKey),
                    List.of(String.valueOf(limit), String.valueOf(windowSec), "1", String.valueOf(nowMs))
                ).next().map(rawList -> parseResult((List<?>) rawList, limit, algo));
                break;
            }
            case "SLIDING_WINDOW": {
                String redisKey = "rl:sw:" + keyHash;
                long windowMs = windowSec * 1000L;
                redisMono = redisTemplate.execute(
                    slidingWindowScript,
                    List.of(redisKey),
                    List.of(String.valueOf(limit), String.valueOf(windowMs), "1", String.valueOf(nowMs), UUID.randomUUID().toString())
                ).next().map(rawList -> parseResult((List<?>) rawList, limit, algo));
                break;
            }
            case "LEAKY_BUCKET": {
                String redisKey = "rl:lb:" + keyHash;
                redisMono = redisTemplate.execute(
                    leakyBucketScript,
                    List.of(redisKey),
                    List.of(String.valueOf(capacity), String.valueOf(refillRate), "1", String.valueOf(nowMs))
                ).next().map(rawList -> parseResult((List<?>) rawList, capacity, algo));
                break;
            }
            case "TOKEN_BUCKET":
            default: {
                String redisKey = "rl:tb:" + keyHash;
                redisMono = redisTemplate.execute(
                    tokenBucketScript,
                    List.of(redisKey),
                    List.of(String.valueOf(capacity), String.valueOf(refillRate), "1", String.valueOf(nowMs))
                ).next().map(rawList -> parseResult((List<?>) rawList, capacity, algo));
                break;
            }
        }

        return redisMono.onErrorResume(e -> {
            log.warn("Redis rate limit evaluation failed for {}: {}. Using in-memory rate limiting engine.", keyHash, e.getMessage());
            return Mono.just(evaluateInMemory(keyHash, algo, limit, windowSec, capacity, refillRate, nowMs));
        });
    }

    private EvaluationResult evaluateInMemory(String keyHash, String algo, long limit, long windowSec, long capacity, double refillRate, long nowMs) {
        switch (algo) {
            case "FIXED_WINDOW": {
                long bucket = nowMs / (windowSec * 1000L);
                String mapKey = keyHash + ":" + bucket;
                AtomicLong counter = localFixedWindows.computeIfAbsent(mapKey, k -> new AtomicLong(0));
                long current = counter.incrementAndGet();
                long timeInWindow = (nowMs % (windowSec * 1000L)) / 1000L;
                long resetSeconds = Math.max(1, windowSec - timeInWindow);
                if (current <= limit) {
                    return new EvaluationResult(true, limit, limit - current, resetSeconds, 0L, algo);
                } else {
                    return new EvaluationResult(false, limit, 0L, resetSeconds, resetSeconds, algo);
                }
            }
            case "SLIDING_WINDOW": {
                long windowMs = windowSec * 1000L;
                Deque<Long> timestamps = localSlidingWindows.computeIfAbsent(keyHash, k -> new ArrayDeque<>());
                synchronized (timestamps) {
                    long windowStart = nowMs - windowMs;
                    while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                        timestamps.pollFirst();
                    }
                    if (timestamps.size() + 1 <= limit) {
                        timestamps.addLast(nowMs);
                        long remaining = limit - timestamps.size();
                        long resetSeconds = Math.max(1, windowSec);
                        return new EvaluationResult(true, limit, remaining, resetSeconds, 0L, algo);
                    } else {
                        long oldest = timestamps.isEmpty() ? nowMs : timestamps.peekFirst();
                        long waitMs = Math.max(1000L, (oldest + windowMs) - nowMs);
                        long retryAfterSeconds = (long) Math.ceil(waitMs / 1000.0);
                        return new EvaluationResult(false, limit, 0L, retryAfterSeconds, retryAfterSeconds, algo);
                    }
                }
            }
            case "LEAKY_BUCKET": {
                LocalLeakyBucket bucket = localLeakyBuckets.computeIfAbsent(keyHash, k -> new LocalLeakyBucket(0.0, nowMs));
                synchronized (bucket) {
                    long deltaMs = Math.max(0, nowMs - bucket.lastLeakMs);
                    double leaked = (deltaMs / 1000.0) * refillRate;
                    bucket.water = Math.max(0.0, bucket.water - leaked);
                    bucket.lastLeakMs = nowMs;
                    if (bucket.water + 1.0 <= capacity) {
                        bucket.water += 1.0;
                        long remaining = (long) Math.floor(capacity - bucket.water);
                        long resetSeconds = (long) Math.ceil(bucket.water / Math.max(0.1, refillRate));
                        return new EvaluationResult(true, capacity, remaining, resetSeconds, 0L, algo);
                    } else {
                        double excess = (bucket.water + 1.0) - capacity;
                        long retryAfterSeconds = Math.max(1, (long) Math.ceil(excess / Math.max(0.1, refillRate)));
                        long resetSeconds = (long) Math.ceil(bucket.water / Math.max(0.1, refillRate));
                        return new EvaluationResult(false, capacity, 0L, resetSeconds, retryAfterSeconds, algo);
                    }
                }
            }
            case "TOKEN_BUCKET":
            default: {
                LocalBucket bucket = localTokenBuckets.computeIfAbsent(keyHash, k -> new LocalBucket(capacity, nowMs));
                synchronized (bucket) {
                    long deltaMs = Math.max(0, nowMs - bucket.lastRefillMs);
                    double tokensToAdd = (deltaMs / 1000.0) * refillRate;
                    bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd);
                    bucket.lastRefillMs = nowMs;
                    if (bucket.tokens >= 1.0) {
                        bucket.tokens -= 1.0;
                        long remaining = (long) Math.floor(bucket.tokens);
                        long resetSeconds = (long) Math.ceil((capacity - bucket.tokens) / Math.max(0.1, refillRate));
                        return new EvaluationResult(true, capacity, remaining, resetSeconds, 0L, algo);
                    } else {
                        double missing = 1.0 - bucket.tokens;
                        long retryAfterSeconds = Math.max(1, (long) Math.ceil(missing / Math.max(0.1, refillRate)));
                        long resetSeconds = (long) Math.ceil((capacity - bucket.tokens) / Math.max(0.1, refillRate));
                        return new EvaluationResult(false, capacity, (long) Math.floor(bucket.tokens), resetSeconds, retryAfterSeconds, algo);
                    }
                }
            }
        }
    }

    private EvaluationResult parseResult(List<?> list, long defaultLimit, String algo) {
        if (list == null || list.size() < 4) {
            return new EvaluationResult(true, defaultLimit, defaultLimit - 1, 60, 0, algo);
        }
        boolean allowed = Long.parseLong(list.get(0).toString()) == 1L;
        long remaining = Long.parseLong(list.get(1).toString());
        long resetSeconds = Math.max(1, Long.parseLong(list.get(2).toString()));
        long retryAfterSeconds = allowed ? 0L : Math.max(1, Long.parseLong(list.get(3).toString()));

        return new EvaluationResult(allowed, defaultLimit, remaining, resetSeconds, retryAfterSeconds, algo);
    }

    private void recordMetrics(String outcome, String algorithm, String policyName, int status, long responseTimeMs) {
        if (meterRegistry == null) return;
        try {
            String safeAlgo = algorithm != null ? algorithm : "UNKNOWN";
            String safePolicy = policyName != null ? policyName : "UNKNOWN";
            Counter.builder("ratelimiter.requests.total")
                .tag("outcome", outcome)
                .tag("algorithm", safeAlgo)
                .tag("policy", safePolicy)
                .tag("status", String.valueOf(status))
                .register(meterRegistry)
                .increment();

            Timer.builder("ratelimiter.requests.duration")
                .tag("outcome", outcome)
                .tag("algorithm", safeAlgo)
                .register(meterRegistry)
                .record(responseTimeMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("Non-critical: metrics recording failed: {}", e.getMessage());
        }
    }

    private String extractApiKey(ServerHttpRequest request) {
        String key = request.getHeaders().getFirst("X-API-Key");
        if (StringUtils.hasText(key)) return key.trim();

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer arl_")) {
            return authHeader.substring(7).trim();
        }

        String queryKey = request.getQueryParams().getFirst("api_key");
        if (StringUtils.hasText(queryKey)) return queryKey.trim();

        return null;
    }

    private String hashKey(String rawKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawKey.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null 
            ? request.getRemoteAddress().getAddress().getHostAddress() 
            : "unknown";
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String error, String message, String correlationId) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("X-Correlation-ID", correlationId);

        String json = String.format(
            "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"correlationId\":\"%s\",\"timestamp\":\"%s\"}",
            status.value(), error, message, correlationId, Instant.now().toString()
        );

        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> write429Response(ServerWebExchange exchange, ApiKeyPolicyInfo policy, EvaluationResult result, String correlationId) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String message = String.format("Rate limit exceeded for policy '%s'. Please retry after %d seconds.",
            policy.policyName() != null ? policy.policyName() : "Standard", result.retryAfterSeconds());

        String json = String.format(
            "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"%s\",\"algorithm\":\"%s\",\"limit\":%d,\"remaining\":0,\"retryAfterSeconds\":%d,\"correlationId\":\"%s\",\"timestamp\":\"%s\"}",
            message, result.algorithm(), result.limit(), result.retryAfterSeconds(), correlationId, Instant.now().toString()
        );

        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
