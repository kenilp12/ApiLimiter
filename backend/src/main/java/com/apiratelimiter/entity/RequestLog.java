package com.apiratelimiter.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "request_logs")
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_prefix", length = 30)
    private String apiKeyPrefix;

    @Column(nullable = false, length = 255)
    private String endpoint;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @Column(name = "is_allowed", nullable = false)
    private Boolean isAllowed;

    @Column(name = "rate_limit_limit")
    private Long rateLimitLimit;

    @Column(name = "rate_limit_remaining")
    private Long rateLimitRemaining;

    @Column(length = 30)
    private String algorithm;

    @Column(name = "policy_name", length = 100)
    private String policyName;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RequestLog() {}

    public RequestLog(String apiKeyPrefix, String endpoint, String method, String clientIp,
                      Integer statusCode, Boolean isAllowed, Long rateLimitLimit,
                      Long rateLimitRemaining, String algorithm, String policyName, Long responseTimeMs) {
        this.apiKeyPrefix = apiKeyPrefix;
        this.endpoint = endpoint;
        this.method = method;
        this.clientIp = clientIp;
        this.statusCode = statusCode;
        this.isAllowed = isAllowed;
        this.rateLimitLimit = rateLimitLimit;
        this.rateLimitRemaining = rateLimitRemaining;
        this.algorithm = algorithm;
        this.policyName = policyName;
        this.responseTimeMs = responseTimeMs;
        this.createdAt = Instant.now();
    }

    public RequestLog(String apiKeyPrefix, String endpoint, String method, String clientIp,
                      Integer statusCode, Boolean isAllowed, Long rateLimitLimit,
                      Long rateLimitRemaining, String algorithm, Long responseTimeMs) {
        this(apiKeyPrefix, endpoint, method, clientIp, statusCode, isAllowed, rateLimitLimit, rateLimitRemaining, algorithm, null, responseTimeMs);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getApiKeyPrefix() { return apiKeyPrefix; }
    public void setApiKeyPrefix(String apiKeyPrefix) { this.apiKeyPrefix = apiKeyPrefix; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }

    public Boolean getIsAllowed() { return isAllowed; }
    public void setIsAllowed(Boolean allowed) { isAllowed = allowed; }

    public Long getRateLimitLimit() { return rateLimitLimit; }
    public void setRateLimitLimit(Long rateLimitLimit) { this.rateLimitLimit = rateLimitLimit; }

    public Long getRateLimitRemaining() { return rateLimitRemaining; }
    public void setRateLimitRemaining(Long rateLimitRemaining) { this.rateLimitRemaining = rateLimitRemaining; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }

    public Long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Long responseTimeMs) { this.responseTimeMs = responseTimeMs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
