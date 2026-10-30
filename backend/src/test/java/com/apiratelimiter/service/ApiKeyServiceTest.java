package com.apiratelimiter.service;

import com.apiratelimiter.dto.ApiKeyCreateRequest;
import com.apiratelimiter.dto.ApiKeyCreatedResponse;
import com.apiratelimiter.dto.ApiKeyValidationResponse;
import com.apiratelimiter.entity.ApiKey;
import com.apiratelimiter.entity.RateLimitPolicy;
import com.apiratelimiter.entity.User;
import com.apiratelimiter.enums.AlgorithmType;
import com.apiratelimiter.enums.Role;
import com.apiratelimiter.repository.ApiKeyRepository;
import com.apiratelimiter.repository.RateLimitPolicyRepository;
import com.apiratelimiter.repository.UserRepository;
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
class ApiKeyServiceTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private RateLimitPolicyRepository policyRepository;
    @Mock private UserRepository userRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private AuditService auditService;

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository, policyRepository, userRepository, redisTemplate, auditService);
    }

    @Test
    @DisplayName("Create API Key should return raw key and save hashed key")
    void testCreateApiKey() {
        User user = new User("tester", "test@test.com", "pass", Role.ROLE_USER);
        user.setId(1L);
        RateLimitPolicy policy = new RateLimitPolicy("Standard", "Desc", AlgorithmType.TOKEN_BUCKET, 100L, 60L, 100L, 1.0, true);
        policy.setId(10L);

        when(policyRepository.findById(10L)).thenReturn(Optional.of(policy));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey k = invocation.getArgument(0);
            k.setId(99L);
            return k;
        });

        UserPrincipal principal = new UserPrincipal(1L, "tester", "test@test.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        ApiKeyCreateRequest req = new ApiKeyCreateRequest("Test Key", 10L, null);

        ApiKeyCreatedResponse response = apiKeyService.createApiKey(req, principal);

        assertNotNull(response);
        assertNotNull(response.rawApiKey(), "Raw API key must be returned once to client");
        assertTrue(response.rawApiKey().startsWith("arl_live_"));
        assertEquals("Test Key", response.apiKey().name());
    }

    @Test
    @DisplayName("Resolve key by hash returns active policy metadata")
    void testResolveKeyByHash() {
        User user = new User("tester", "test@test.com", "pass", Role.ROLE_USER);
        RateLimitPolicy policy = new RateLimitPolicy("Standard", "Desc", AlgorithmType.TOKEN_BUCKET, 100L, 60L, 100L, 1.0, true);
        policy.setId(10L);
        ApiKey apiKey = new ApiKey("arl_live_abc", "hash_123", "Key 1", policy, user, null);

        when(apiKeyRepository.findByKeyHash("hash_123")).thenReturn(Optional.of(apiKey));

        ApiKeyValidationResponse validation = apiKeyService.resolveKeyByHash("hash_123");
        assertTrue(validation.valid());
        assertEquals("Standard", validation.policyName());
        assertEquals(100L, validation.limitValue());
    }

    @Test
    @DisplayName("Revoke key marks key as REVOKED and publishes Redis invalidation")
    void testRevokeKeyPublishesInvalidation() {
        User user = new User("tester", "test@test.com", "pass", Role.ROLE_USER);
        user.setId(1L);
        RateLimitPolicy policy = new RateLimitPolicy("Standard", "Desc", AlgorithmType.TOKEN_BUCKET, 100L, 60L, 100L, 1.0, true);
        policy.setId(10L);
        ApiKey apiKey = new ApiKey("arl_live_abc", "hash_123", "Key 1", policy, user, null);
        apiKey.setId(5L);

        when(apiKeyRepository.findById(5L)).thenReturn(Optional.of(apiKey));
        org.springframework.data.redis.connection.RedisConnectionFactory connFactory = mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(connFactory);

        UserPrincipal principal = new UserPrincipal(1L, "tester", "test@test.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        apiKeyService.revokeKey(5L, principal);

        assertEquals(com.apiratelimiter.enums.ApiKeyStatus.REVOKED, apiKey.getStatus());
        verify(redisTemplate).delete("cache:apikey:hash_123");
        verify(redisTemplate).convertAndSend("rl:cache:invalidation", "hash_123");
    }

    @Test
    @DisplayName("Create API Key with inactive policy throws BadRequestException")
    void testCreateApiKeyInactivePolicyThrows() {
        RateLimitPolicy policy = new RateLimitPolicy("Inactive", "Desc", AlgorithmType.TOKEN_BUCKET, 100L, 60L, 100L, 1.0, false);
        policy.setId(10L);
        when(policyRepository.findById(10L)).thenReturn(Optional.of(policy));

        UserPrincipal principal = new UserPrincipal(1L, "tester", "test@test.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        ApiKeyCreateRequest req = new ApiKeyCreateRequest("Key", 10L, null);

        assertThrows(com.apiratelimiter.exception.BadRequestException.class, () -> {
            apiKeyService.createApiKey(req, principal);
        });
    }
}
