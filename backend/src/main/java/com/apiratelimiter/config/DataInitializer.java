package com.apiratelimiter.config;

import com.apiratelimiter.entity.ApiKey;
import com.apiratelimiter.entity.RateLimitPolicy;
import com.apiratelimiter.entity.RequestLog;
import com.apiratelimiter.entity.User;
import com.apiratelimiter.enums.AlgorithmType;
import com.apiratelimiter.enums.Role;
import com.apiratelimiter.repository.ApiKeyRepository;
import com.apiratelimiter.repository.RateLimitPolicyRepository;
import com.apiratelimiter.repository.RequestLogRepository;
import com.apiratelimiter.repository.UserRepository;
import com.apiratelimiter.util.ApiKeyHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final RateLimitPolicyRepository policyRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final RequestLogRepository requestLogRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
        UserRepository userRepository,
        RateLimitPolicyRepository policyRepository,
        ApiKeyRepository apiKeyRepository,
        RequestLogRepository requestLogRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.policyRepository = policyRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.requestLogRepository = requestLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already initialized. Skipping seeding.");
            return;
        }

        log.info("Seeding initial platform data (users, policies, demo api key, logs)...");

        // 1. Users
        User admin = new User("admin", "admin@apiratelimiter.io", passwordEncoder.encode("admin123"), Role.ROLE_ADMIN);
        User dev = new User("developer", "developer@apiratelimiter.io", passwordEncoder.encode("dev123"), Role.ROLE_USER);
        userRepository.saveAll(List.of(admin, dev));

        // 2. Policies for all 4 algorithms
        RateLimitPolicy p1 = new RateLimitPolicy(
            "Fixed Window Standard",
            "Fixed window counter allowing up to 30 requests per minute.",
            AlgorithmType.FIXED_WINDOW,
            30L, 60L, 30L, 0.5, true
        );

        RateLimitPolicy p2 = new RateLimitPolicy(
            "Sliding Window Precise",
            "Continuous sliding window log with millisecond precision, 20 requests per minute.",
            AlgorithmType.SLIDING_WINDOW,
            20L, 60L, 20L, 0.33, true
        );

        RateLimitPolicy p3 = new RateLimitPolicy(
            "Token Bucket Burst Tier",
            "Allows short traffic bursts up to 20 tokens, refilling steadily at 5 tokens/second.",
            AlgorithmType.TOKEN_BUCKET,
            10L, 10L, 20L, 5.0, true
        );

        RateLimitPolicy p4 = new RateLimitPolicy(
            "Leaky Bucket Smooth Traffic",
            "Smoothes incoming traffic by leaking processed requests at a constant rate of 3/sec.",
            AlgorithmType.LEAKY_BUCKET,
            15L, 15L, 15L, 3.0, true
        );

        policyRepository.saveAll(List.of(p1, p2, p3, p4));

        // 3. Demo API Key
        String demoRawKey = "arl_live_demo1234567890abcdef1234";
        String demoHash = ApiKeyHasher.hashKey(demoRawKey);
        String demoPrefix = ApiKeyHasher.extractPrefix(demoRawKey);

        ApiKey demoKey = new ApiKey(
            demoPrefix,
            demoHash,
            "Demo Playground Key",
            p3,
            admin,
            Instant.now().plus(365, ChronoUnit.DAYS)
        );
        apiKeyRepository.save(demoKey);

        // 4. Seed initial realistic traffic for instant analytics visualization
        Random random = new Random(42);
        String[] endpoints = {"/protected-api/data", "/protected-api/users", "/protected-api/orders", "/protected-api/echo"};
        String[] algorithms = {"TOKEN_BUCKET", "SLIDING_WINDOW", "FIXED_WINDOW", "LEAKY_BUCKET"};
        Instant now = Instant.now();

        for (int i = 0; i < 60; i++) {
            Instant timestamp = now.minus(random.nextInt(60), ChronoUnit.MINUTES);
            boolean isAllowed = random.nextDouble() > 0.18; // ~18% rejection rate
            int statusCode = isAllowed ? 200 : 429;
            String endpoint = endpoints[random.nextInt(endpoints.length)];
            String algo = algorithms[random.nextInt(algorithms.length)];
            long responseTime = isAllowed ? (25 + random.nextInt(120)) : (5 + random.nextInt(15));
            long remaining = isAllowed ? random.nextInt(15) : 0L;

            RequestLog logEntry = new RequestLog(
                demoPrefix,
                endpoint,
                "GET",
                "127.0.0.1",
                statusCode,
                isAllowed,
                20L,
                remaining,
                algo,
                responseTime
            );
            logEntry.setCreatedAt(timestamp);
            requestLogRepository.save(logEntry);
        }

        log.info("Seed data successfully created! Demo Key: {}", demoRawKey);
    }
}
