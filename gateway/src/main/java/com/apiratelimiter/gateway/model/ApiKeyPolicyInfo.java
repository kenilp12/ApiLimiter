package com.apiratelimiter.gateway.model;

public record ApiKeyPolicyInfo(
    boolean valid,
    String keyPrefix,
    Long policyId,
    String policyName,
    String algorithm,
    Long limitValue,
    Long timeWindowSeconds,
    Long burstCapacity,
    Double refillRate,
    String reason
) {
    public static ApiKeyPolicyInfo invalid(String reason) {
        return new ApiKeyPolicyInfo(false, null, null, null, null, null, null, null, null, reason);
    }
}
