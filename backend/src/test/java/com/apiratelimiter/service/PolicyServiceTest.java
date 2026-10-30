package com.apiratelimiter.service;

import com.apiratelimiter.dto.PolicyResponse;
import com.apiratelimiter.dto.PolicyUpdateRequest;
import com.apiratelimiter.entity.RateLimitPolicy;
import com.apiratelimiter.enums.AlgorithmType;
import com.apiratelimiter.repository.RateLimitPolicyRepository;
import com.apiratelimiter.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock private RateLimitPolicyRepository policyRepository;
    @Mock private AuditService auditService;
    @Mock private StringRedisTemplate redisTemplate;

    private PolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(policyRepository, auditService, redisTemplate);
    }

    @Test
    @DisplayName("Updating policy publishes invalidation signal to Redis")
    void testUpdatePolicyPublishesInvalidation() {
        RateLimitPolicy existing = new RateLimitPolicy("Standard", "Desc", AlgorithmType.TOKEN_BUCKET, 100L, 60L, 100L, 1.0, true);
        existing.setId(10L);

        when(policyRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(policyRepository.findByName("Updated Standard")).thenReturn(Optional.empty());
        when(policyRepository.save(any(RateLimitPolicy.class))).thenAnswer(i -> i.getArgument(0));

        UserPrincipal principal = new UserPrincipal(1L, "admin", "admin@test.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        PolicyUpdateRequest updateReq = new PolicyUpdateRequest(
            "Updated Standard", "New Desc", AlgorithmType.FIXED_WINDOW, 50L, 30L, 50L, 1.66, true
        );

        PolicyResponse res = policyService.updatePolicy(10L, updateReq, principal);

        assertNotNull(res);
        assertEquals("Updated Standard", res.name());
        assertEquals(AlgorithmType.FIXED_WINDOW, res.algorithm());
        assertEquals(50L, res.limitValue());
        verify(redisTemplate).convertAndSend("rl:cache:invalidation", "ALL");
    }

    @Test
    @DisplayName("Toggling policy status publishes invalidation signal to Redis")
    void testToggleStatusPublishesInvalidation() {
        RateLimitPolicy existing = new RateLimitPolicy("Standard", "Desc", AlgorithmType.TOKEN_BUCKET, 100L, 60L, 100L, 1.0, true);
        existing.setId(10L);

        when(policyRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(policyRepository.save(any(RateLimitPolicy.class))).thenAnswer(i -> i.getArgument(0));

        UserPrincipal principal = new UserPrincipal(1L, "admin", "admin@test.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        PolicyResponse res = policyService.toggleStatus(10L, false, principal);

        assertFalse(res.isActive());
        verify(redisTemplate).convertAndSend("rl:cache:invalidation", "ALL");
    }
}

