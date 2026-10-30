package com.apiratelimiter.service;

import com.apiratelimiter.dto.PolicyCreateRequest;
import com.apiratelimiter.dto.PolicyResponse;
import com.apiratelimiter.dto.PolicyUpdateRequest;
import com.apiratelimiter.entity.RateLimitPolicy;
import com.apiratelimiter.exception.BadRequestException;
import com.apiratelimiter.exception.ResourceNotFoundException;
import com.apiratelimiter.repository.RateLimitPolicyRepository;
import com.apiratelimiter.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PolicyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyService.class);
    private static final String REDIS_INVALIDATION_TOPIC = "rl:cache:invalidation";

    private final RateLimitPolicyRepository policyRepository;
    private final AuditService auditService;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public PolicyService(
        RateLimitPolicyRepository policyRepository,
        AuditService auditService,
        @Autowired(required = false) StringRedisTemplate redisTemplate
    ) {
        this.policyRepository = policyRepository;
        this.auditService = auditService;
        this.redisTemplate = redisTemplate;
    }

    public PolicyService(RateLimitPolicyRepository policyRepository, AuditService auditService) {
        this(policyRepository, auditService, null);
    }

    @Transactional
    public PolicyResponse createPolicy(PolicyCreateRequest request, UserPrincipal principal) {
        if (policyRepository.findByName(request.name().trim()).isPresent()) {
            throw new BadRequestException("A policy with this name already exists");
        }

        RateLimitPolicy policy = new RateLimitPolicy(
            request.name().trim(),
            request.description(),
            request.algorithm(),
            request.limitValue(),
            request.timeWindowSeconds(),
            request.burstCapacity() != null ? request.burstCapacity() : request.limitValue(),
            request.refillRate() != null ? request.refillRate() : (double) request.limitValue() / request.timeWindowSeconds(),
            request.isActive() != null ? request.isActive() : true
        );

        RateLimitPolicy saved = policyRepository.save(policy);
        auditService.record("CREATE", "POLICY", saved.getId().toString(), principal.getUsername(), "Created policy: " + saved.getName());
        return PolicyResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> listPolicies() {
        return policyRepository.findAll().stream()
            .map(PolicyResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public PolicyResponse getPolicy(Long id) {
        RateLimitPolicy policy = policyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Policy not found with id: " + id));
        return PolicyResponse.fromEntity(policy);
    }

    @Transactional
    public PolicyResponse updatePolicy(Long id, PolicyUpdateRequest request, UserPrincipal principal) {
        RateLimitPolicy policy = policyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Policy not found with id: " + id));

        policyRepository.findByName(request.name().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BadRequestException("Another policy with this name already exists");
            }
        });

        policy.setName(request.name().trim());
        policy.setDescription(request.description());
        policy.setAlgorithm(request.algorithm());
        policy.setLimitValue(request.limitValue());
        policy.setTimeWindowSeconds(request.timeWindowSeconds());
        policy.setBurstCapacity(request.burstCapacity() != null ? request.burstCapacity() : request.limitValue());
        policy.setRefillRate(request.refillRate() != null ? request.refillRate() : (double) request.limitValue() / request.timeWindowSeconds());
        if (request.isActive() != null) {
            policy.setIsActive(request.isActive());
        }

        RateLimitPolicy updated = policyRepository.save(policy);
        publishInvalidation();
        auditService.record("UPDATE", "POLICY", updated.getId().toString(), principal.getUsername(), "Updated policy: " + updated.getName());
        return PolicyResponse.fromEntity(updated);
    }

    @Transactional
    public void deletePolicy(Long id, UserPrincipal principal) {
        RateLimitPolicy policy = policyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Policy not found with id: " + id));

        policyRepository.delete(policy);
        publishInvalidation();
        auditService.record("DELETE", "POLICY", id.toString(), principal.getUsername(), "Deleted policy: " + policy.getName());
    }

    @Transactional
    public PolicyResponse toggleStatus(Long id, boolean active, UserPrincipal principal) {
        RateLimitPolicy policy = policyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Policy not found with id: " + id));

        policy.setIsActive(active);
        RateLimitPolicy saved = policyRepository.save(policy);
        publishInvalidation();
        auditService.record("STATUS_CHANGE", "POLICY", id.toString(), principal.getUsername(), "Set policy active=" + active);
        return PolicyResponse.fromEntity(saved);
    }

    private void publishInvalidation() {
        try {
            if (redisTemplate != null) {
                redisTemplate.convertAndSend(REDIS_INVALIDATION_TOPIC, "ALL");
            }
        } catch (Exception e) {
            log.warn("Failed to publish policy invalidation signal to Redis: {}", e.getMessage());
        }
    }
}
