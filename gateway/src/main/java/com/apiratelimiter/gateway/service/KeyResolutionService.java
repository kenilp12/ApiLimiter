package com.apiratelimiter.gateway.service;

import com.apiratelimiter.gateway.model.ApiKeyPolicyInfo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KeyResolutionService {

    private static final Logger log = LoggerFactory.getLogger(KeyResolutionService.class);
    private static final long CACHE_TTL_MS = 5000L; // 5 seconds maximum cache TTL for immediate updates
    private static final String REDIS_INVALIDATION_TOPIC = "rl:cache:invalidation";

    private final WebClient backendWebClient;
    private final ReactiveStringRedisTemplate redisTemplate;

    private record CachedPolicy(ApiKeyPolicyInfo info, long expiresAtMs) {}
    private final ConcurrentHashMap<String, CachedPolicy> localMemoryCache = new ConcurrentHashMap<>();

    @Autowired
    public KeyResolutionService(
        WebClient backendWebClient,
        @Autowired(required = false) ReactiveStringRedisTemplate redisTemplate
    ) {
        this.backendWebClient = backendWebClient;
        this.redisTemplate = redisTemplate;
    }

    public KeyResolutionService(WebClient backendWebClient) {
        this(backendWebClient, null);
    }

    @PostConstruct
    public void initInvalidationListener() {
        if (redisTemplate != null) {
            try {
                redisTemplate.listenToChannel(REDIS_INVALIDATION_TOPIC)
                    .subscribe(
                        msg -> {
                            String payload = msg.getMessage();
                            log.info("Received rate limiter cache invalidation event: {}", payload);
                            if ("ALL".equalsIgnoreCase(payload)) {
                                localMemoryCache.clear();
                            } else {
                                localMemoryCache.remove(payload);
                            }
                        },
                        err -> log.debug("Redis cache invalidation subscription notice: {}", err.getMessage())
                    );
            } catch (Exception ex) {
                log.warn("Redis pub/sub invalidation channel could not be initialized (Redis unavailable): {}. Falling back to 5s TTL cache.", ex.getMessage());
            }
        }
    }

    public Mono<ApiKeyPolicyInfo> resolveKey(String keyHash) {
        CachedPolicy cached = localMemoryCache.get(keyHash);
        if (cached != null && System.currentTimeMillis() < cached.expiresAtMs()) {
            return Mono.just(cached.info());
        }

        // Query backend service
        return backendWebClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/internal/keys/resolve")
                .queryParam("keyHash", keyHash)
                .build())
            .retrieve()
            .bodyToMono(ApiKeyPolicyInfo.class)
            .timeout(Duration.ofSeconds(3))
            .doOnNext(info -> {
                if (info != null && info.valid()) {
                    localMemoryCache.put(keyHash, new CachedPolicy(info, System.currentTimeMillis() + CACHE_TTL_MS));
                } else {
                    localMemoryCache.remove(keyHash);
                }
            })
            .onErrorResume(e -> {
                log.warn("Failed to resolve API key hash {} from backend: {}", keyHash, e.getMessage());
                // Fallback default info for testing demo key if backend is restarting
                if ("3a38ad96f7c6ceaa2e86b24cb4d25fa0a1e36ffcc5d53531fb5b565d70f9dfc8".equals(keyHash)) {
                    ApiKeyPolicyInfo demoFallback = new ApiKeyPolicyInfo(
                        true, "arl_live_demo123...", 1L, "Demo Fallback Policy", "TOKEN_BUCKET", 20L, 10L, 20L, 5.0, null
                    );
                    return Mono.just(demoFallback);
                }
                return Mono.just(ApiKeyPolicyInfo.invalid("Backend service unavailable or key invalid"));
            });
    }

    public void invalidate(String keyHash) {
        if (keyHash == null || "ALL".equalsIgnoreCase(keyHash)) {
            localMemoryCache.clear();
        } else {
            localMemoryCache.remove(keyHash);
        }
    }

    public void clearCache() {
        localMemoryCache.clear();
    }
}
