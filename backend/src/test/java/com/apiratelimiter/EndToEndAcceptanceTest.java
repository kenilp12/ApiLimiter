package com.apiratelimiter;

import com.apiratelimiter.dto.ApiKeyCreateRequest;
import com.apiratelimiter.dto.ApiKeyCreatedResponse;
import com.apiratelimiter.dto.LogIngestRequest;
import com.apiratelimiter.dto.PolicyCreateRequest;
import com.apiratelimiter.dto.PolicyResponse;
import com.apiratelimiter.dto.PolicyUpdateRequest;
import com.apiratelimiter.entity.User;
import com.apiratelimiter.enums.AlgorithmType;
import com.apiratelimiter.enums.Role;
import com.apiratelimiter.ratelimit.engine.RateLimiterEngine;
import com.apiratelimiter.ratelimit.model.RateLimitResult;
import com.apiratelimiter.ratelimit.model.RateLimitRule;
import com.apiratelimiter.repository.RequestLogRepository;
import com.apiratelimiter.repository.UserRepository;
import com.apiratelimiter.security.UserPrincipal;
import com.apiratelimiter.service.ApiKeyService;
import com.apiratelimiter.service.PolicyService;
import com.apiratelimiter.util.ApiKeyHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EndToEndAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RequestLogRepository requestLogRepository;
    @Autowired
    private PolicyService policyService;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private RateLimiterEngine rateLimiterEngine;

    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        requestLogRepository.deleteAll();

        User adminUser = userRepository.findByUsername("admin").orElseGet(() -> {
            User u = new User("admin", "admin@acceptance.io", "pass", Role.ROLE_ADMIN);
            return userRepository.save(u);
        });

        adminPrincipal = new UserPrincipal(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getEmail(),
                adminUser.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(adminPrincipal, null,
                adminPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("End-to-End: Step 5-7 Full Lifecycle (Policy, Key, Rate-Limiting, Blocked Logging, Analytics, Revocation)")
    void testEndToEndLifecycle() throws Exception {
        // 1. Create a Rate Limit Policy: 5 req / 10s FIXED_WINDOW
        PolicyCreateRequest policyReq = new PolicyCreateRequest(
                "E2E 5req-10s Fixed Window",
                "Fixed window policy for acceptance testing",
                AlgorithmType.FIXED_WINDOW,
                5L,
                10L,
                5L,
                0.5,
                true);

        PolicyResponse createdPolicy = policyService.createPolicy(policyReq, adminPrincipal);
        assertNotNull(createdPolicy.id());
        assertEquals("FIXED_WINDOW", createdPolicy.algorithm().name());

        // 2. Generate an API Key associated with that policy
        ApiKeyCreateRequest keyReq = new ApiKeyCreateRequest("E2E Acceptance Key", createdPolicy.id(), null);
        ApiKeyCreatedResponse keyResponse = apiKeyService.createApiKey(keyReq, adminPrincipal);

        assertNotNull(keyResponse.rawApiKey());
        assertTrue(keyResponse.rawApiKey().startsWith("arl_live_"));
        Long keyId = keyResponse.apiKey().id();

        // 3. Gateway resolves key by SHA-256 hash
        String keyHash = ApiKeyHasher.hashKey(keyResponse.rawApiKey());
        mockMvc.perform(get("/api/internal/keys/resolve")
                .param("keyHash", keyHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.policyName").value("E2E 5req-10s Fixed Window"))
                .andExpect(jsonPath("$.algorithm").value("FIXED_WINDOW"))
                .andExpect(jsonPath("$.limitValue").value(5));

        // 4. Rate-Limiting Engine evaluation:
        // Send 5 allowed requests
        RateLimitRule rule = new RateLimitRule(keyHash, AlgorithmType.FIXED_WINDOW, 5L, 10L, 5L, 0.5);
        for (int i = 1; i <= 5; i++) {
            RateLimitResult result = rateLimiterEngine.checkRateLimit(keyHash, rule, 1);
            assertTrue(result.allowed(), "Request " + i + " must be allowed");
            assertEquals(5 - i, result.remaining());
        }

        // 5. Send 6th request: must be blocked with HTTP 429
        RateLimitResult blockedResult = rateLimiterEngine.checkRateLimit(keyHash, rule, 1);
        assertFalse(blockedResult.allowed(), "6th request must be blocked");
        assertEquals(0, blockedResult.remaining());
        assertTrue(blockedResult.retryAfterSeconds() >= 1, "Retry-After must be positive");

        // 6. Ingest simulated request logs (5 allowed, 1 blocked 429)
        List<LogIngestRequest.LogEntry> logEntries = List.of(
                new LogIngestRequest.LogEntry(keyResponse.apiKey().keyPrefix(), "/protected-api/data", "GET",
                        "127.0.0.1", 200, true, 5L, 4L, "FIXED_WINDOW", createdPolicy.name(), 12L),
                new LogIngestRequest.LogEntry(keyResponse.apiKey().keyPrefix(), "/protected-api/data", "GET",
                        "127.0.0.1", 200, true, 5L, 3L, "FIXED_WINDOW", createdPolicy.name(), 10L),
                new LogIngestRequest.LogEntry(keyResponse.apiKey().keyPrefix(), "/protected-api/data", "GET",
                        "127.0.0.1", 200, true, 5L, 2L, "FIXED_WINDOW", createdPolicy.name(), 11L),
                new LogIngestRequest.LogEntry(keyResponse.apiKey().keyPrefix(), "/protected-api/data", "GET",
                        "127.0.0.1", 200, true, 5L, 1L, "FIXED_WINDOW", createdPolicy.name(), 14L),
                new LogIngestRequest.LogEntry(keyResponse.apiKey().keyPrefix(), "/protected-api/data", "GET",
                        "127.0.0.1", 200, true, 5L, 0L, "FIXED_WINDOW", createdPolicy.name(), 9L),
                new LogIngestRequest.LogEntry(keyResponse.apiKey().keyPrefix(), "/protected-api/data", "GET",
                        "127.0.0.1", 429, false, 5L, 0L, "FIXED_WINDOW", createdPolicy.name(), 4L));

        mockMvc.perform(post("/api/internal/logs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LogIngestRequest(logEntries))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.count").value(6));

        // 7. Verify /api/analytics/overview reflects the 6 requests
        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(6))
                .andExpect(jsonPath("$.allowedRequests").value(5))
                .andExpect(jsonPath("$.blockedRequests").value(1))
                .andExpect(jsonPath("$.rejectionRatePercentage").value(16.67));

        // 8. Verify /api/analytics/blocked returns the 429 blocked request
        mockMvc.perform(get("/api/analytics/blocked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].statusCode").value(429))
                .andExpect(jsonPath("$.content[0].isAllowed").value(false))
                .andExpect(jsonPath("$.content[0].algorithm").value("FIXED_WINDOW"))
                .andExpect(jsonPath("$.content[0].policyName").value("E2E 5req-10s Fixed Window"))
                .andExpect(jsonPath("$.content[0].endpoint").value("/protected-api/data"));

        // 9. Verify /api/analytics/blocked with filter matches
        mockMvc.perform(get("/api/analytics/blocked")
                .param("endpoint", "/protected-api/data")
                .param("algorithm", "FIXED_WINDOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // 10. Verify /api/analytics/blocked filter with non-matching filter returns 0
        mockMvc.perform(get("/api/analytics/blocked")
                .param("algorithm", "TOKEN_BUCKET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // 11. Switch Algorithm to SLIDING_WINDOW
        PolicyUpdateRequest updateReq = new PolicyUpdateRequest(
                "E2E 5req-10s Fixed Window",
                "Updated to Sliding Window",
                AlgorithmType.SLIDING_WINDOW,
                10L,
                20L,
                10L,
                0.5,
                true);
        PolicyResponse updatedPolicy = policyService.updatePolicy(createdPolicy.id(), updateReq, adminPrincipal);
        assertEquals(AlgorithmType.SLIDING_WINDOW, updatedPolicy.algorithm());

        // Verify gateway resolve returns updated SLIDING_WINDOW algorithm
        mockMvc.perform(get("/api/internal/keys/resolve")
                .param("keyHash", keyHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.algorithm").value("SLIDING_WINDOW"))
                .andExpect(jsonPath("$.limitValue").value(10));

        // 12. Revoke API key
        apiKeyService.revokeKey(keyId, adminPrincipal);

        // Verify key resolution now reports valid = false
        mockMvc.perform(get("/api/internal/keys/resolve")
                .param("keyHash", keyHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").value("Key is revoked, expired, or inactive"));
    }
}
