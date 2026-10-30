package com.apiratelimiter.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class GatewayLogService {

    private static final Logger log = LoggerFactory.getLogger(GatewayLogService.class);
    private static final int MAX_QUEUE_CAPACITY = 5000;

    private final WebClient backendWebClient;
    private final ConcurrentLinkedQueue<Map<String, Object>> logQueue = new ConcurrentLinkedQueue<>();

    public GatewayLogService(WebClient backendWebClient) {
        this.backendWebClient = backendWebClient;
    }

    public void queueLog(String apiKeyPrefix, String endpoint, String method, String clientIp,
            int statusCode, boolean isAllowed, Long limit, Long remaining,
            String algorithm, String policyName, long responseTimeMs) {
        // Enforce bounded queue to avoid unbounded memory growth during backend outages
        if (logQueue.size() >= MAX_QUEUE_CAPACITY) {
            logQueue.poll(); // Evict oldest
        }

        Map<String, Object> entry = new HashMap<>();
        entry.put("apiKeyPrefix", apiKeyPrefix);
        entry.put("endpoint", endpoint);
        entry.put("method", method);
        entry.put("clientIp", clientIp);
        entry.put("statusCode", statusCode);
        entry.put("isAllowed", isAllowed);
        entry.put("rateLimitLimit", limit);
        entry.put("rateLimitRemaining", remaining);
        entry.put("algorithm", algorithm);
        entry.put("policyName", policyName);
        entry.put("responseTimeMs", responseTimeMs);

        logQueue.offer(entry);

        if (logQueue.size() >= 50) {
            flushLogs();
        }
    }

    public void queueLog(String apiKeyPrefix, String endpoint, String method, String clientIp,
            int statusCode, boolean isAllowed, Long limit, Long remaining,
            String algorithm, long responseTimeMs) {
        queueLog(apiKeyPrefix, endpoint, method, clientIp, statusCode, isAllowed, limit, remaining, algorithm, null,
                responseTimeMs);
    }

    @Scheduled(fixedDelay = 2000)
    public void flushLogs() {
        if (logQueue.isEmpty())
            return;

        List<Map<String, Object>> batch = new ArrayList<>();
        Map<String, Object> item;
        int count = 0;
        while ((item = logQueue.poll()) != null && count < 100) {
            batch.add(item);
            count++;
        }

        if (batch.isEmpty())
            return;

        backendWebClient.post()
                .uri("/api/internal/logs")
                .bodyValue(Map.of("entries", batch))
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        res -> {
                        },
                        err -> log.debug("Non-critical: log batch push failed: {}", err.getMessage()));
    }
}
