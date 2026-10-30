package com.apiratelimiter.service;

import com.apiratelimiter.dto.*;
import com.apiratelimiter.entity.ApiKey;
import com.apiratelimiter.entity.RateLimitPolicy;
import com.apiratelimiter.entity.User;
import com.apiratelimiter.enums.ApiKeyStatus;
import com.apiratelimiter.enums.Role;
import com.apiratelimiter.exception.BadRequestException;
import com.apiratelimiter.exception.ResourceNotFoundException;
import com.apiratelimiter.repository.ApiKeyRepository;
import com.apiratelimiter.repository.RateLimitPolicyRepository;
import com.apiratelimiter.repository.UserRepository;
import com.apiratelimiter.security.UserPrincipal;
import com.apiratelimiter.util.ApiKeyHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String REDIS_CACHE_PREFIX = "cache:apikey:";

    private final ApiKeyRepository apiKeyRepository;
    private final RateLimitPolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final AuditService auditService;

    public ApiKeyService(
        ApiKeyRepository apiKeyRepository,
        RateLimitPolicyRepository policyRepository,
        UserRepository userRepository,
        StringRedisTemplate redisTemplate,
        AuditService auditService
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.policyRepository = policyRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.auditService = auditService;
    }

    @Transactional
    public ApiKeyCreatedResponse createApiKey(ApiKeyCreateRequest request, UserPrincipal principal) {
        RateLimitPolicy policy = policyRepository.findById(request.policyId())
            .orElseThrow(() -> new ResourceNotFoundException("Rate limit policy not found with id: " + request.policyId()));

        if (!Boolean.TRUE.equals(policy.getIsActive())) {
            throw new BadRequestException("Cannot assign an inactive rate limit policy");
        }

        User user = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String rawKey = ApiKeyHasher.generateRawApiKey();
        String keyHash = ApiKeyHasher.hashKey(rawKey);
        String keyPrefix = ApiKeyHasher.extractPrefix(rawKey);

        ApiKey apiKey = new ApiKey(
            keyPrefix,
            keyHash,
            request.name().trim(),
            policy,
            user,
            request.expiresAt()
        );

        ApiKey saved = apiKeyRepository.save(apiKey);

        // Cache policy in Redis for Gateway ultra-fast resolution
        cacheKeyResolution(keyHash, ApiKeyValidationResponse.active(saved));

        auditService.record("CREATE", "API_KEY", saved.getId().toString(), user.getUsername(), "Created API key: " + saved.getName());

        return new ApiKeyCreatedResponse(ApiKeyResponse.fromEntity(saved), rawKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listKeys(UserPrincipal principal) {
        List<ApiKey> keys;
        boolean isAdmin = principal.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()));

        if (isAdmin) {
            keys = apiKeyRepository.findAll();
        } else {
            keys = apiKeyRepository.findByUserId(principal.getId());
        }

        return keys.stream().map(ApiKeyResponse::fromEntity).toList();
    }

    @Transactional
    public void revokeKey(Long id, UserPrincipal principal) {
        ApiKey apiKey = apiKeyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("API key not found with id: " + id));

        boolean isAdmin = principal.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()));

        if (!isAdmin && !apiKey.getUser().getId().equals(principal.getId())) {
            throw new BadRequestException("You do not have permission to revoke this key");
        }

        apiKey.setStatus(ApiKeyStatus.REVOKED);
        apiKeyRepository.save(apiKey);

        // Evict from Redis cache
        evictKeyResolution(apiKey.getKeyHash());

        auditService.record("REVOKE", "API_KEY", apiKey.getId().toString(), principal.getUsername(), "Revoked API key: " + apiKey.getName());
    }

    @Transactional(readOnly = true)
    public ApiKeyValidationResponse resolveKeyByHash(String keyHash) {
        return apiKeyRepository.findByKeyHash(keyHash)
            .map(key -> {
                if (!key.isValid()) {
                    return ApiKeyValidationResponse.invalid("Key is revoked, expired, or inactive");
                }
                return ApiKeyValidationResponse.active(key);
            })
            .orElseGet(() -> ApiKeyValidationResponse.invalid("Invalid API key"));
    }

    private void cacheKeyResolution(String keyHash, ApiKeyValidationResponse response) {
        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                String redisKey = REDIS_CACHE_PREFIX + keyHash;
                // Cache key metadata for 10 minutes
                redisTemplate.opsForValue().set(redisKey, response.policyName() + ":" + response.algorithm() + ":" + response.limitValue() + ":" + response.timeWindowSeconds(), Duration.ofMinutes(10));
            }
        } catch (Exception e) {
            log.warn("Failed to cache key resolution in Redis: {}", e.getMessage());
        }
    }

    private void evictKeyResolution(String keyHash) {
        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                redisTemplate.delete(REDIS_CACHE_PREFIX + keyHash);
                redisTemplate.convertAndSend("rl:cache:invalidation", keyHash);
            }
        } catch (Exception e) {
            log.warn("Failed to evict key resolution from Redis: {}", e.getMessage());
        }
    }
}
