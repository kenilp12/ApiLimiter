package com.apiratelimiter.ratelimit.engine;

import com.apiratelimiter.enums.AlgorithmType;
import com.apiratelimiter.ratelimit.algorithm.RateLimitAlgorithm;
import com.apiratelimiter.ratelimit.model.RateLimitResult;
import com.apiratelimiter.ratelimit.model.RateLimitRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class RateLimiterEngine {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterEngine.class);

    private final Map<AlgorithmType, RateLimitAlgorithm> algorithms = new EnumMap<>(AlgorithmType.class);
    private final boolean failOpen;

    public RateLimiterEngine(
        List<RateLimitAlgorithm> algorithmList,
        @Value("${ratelimiter.fail-open:false}") boolean failOpen
    ) {
        for (RateLimitAlgorithm algo : algorithmList) {
            this.algorithms.put(algo.getType(), algo);
        }
        this.failOpen = failOpen;
    }

    public RateLimitResult checkRateLimit(String key, RateLimitRule rule, long cost) {
        if (rule == null) {
            // No policy attached -> default allow
            return RateLimitResult.allow(Long.MAX_VALUE, Long.MAX_VALUE, 0, "NONE", key);
        }

        RateLimitAlgorithm algorithm = algorithms.get(rule.algorithm());
        if (algorithm == null) {
            log.error("Unknown algorithm {} requested for key {}", rule.algorithm(), key);
            return failOpen 
                ? RateLimitResult.allow(rule.limit(), rule.limit(), 0, "FAIL_OPEN", key)
                : RateLimitResult.block(rule.limit(), 0, 60, 60, "FAIL_CLOSED", key);
        }

        try {
            return algorithm.evaluate(key, rule, cost);
        } catch (Exception e) {
            log.error("Unexpected error executing algorithm {} for key {}: {}", rule.algorithm(), key, e.getMessage(), e);
            if (failOpen) {
                log.warn("Fail-open active: allowing traffic for key {}", key);
                return RateLimitResult.allow(rule.limit(), 1, 0, rule.algorithm().name(), key);
            } else {
                log.warn("Fail-closed active: rejecting traffic for key {}", key);
                return RateLimitResult.block(rule.limit(), 0, 60, 60, rule.algorithm().name(), key);
            }
        }
    }

    public boolean isFailOpen() {
        return failOpen;
    }
}
