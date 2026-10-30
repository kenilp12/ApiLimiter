package com.apiratelimiter.entity;

import com.apiratelimiter.enums.ApiKeyStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_prefix", nullable = false, length = 30)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApiKeyStatus status = ApiKeyStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rate_limit_policy_id", nullable = false)
    private RateLimitPolicy rateLimitPolicy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ApiKey() {}

    public ApiKey(String keyPrefix, String keyHash, String name,
                  RateLimitPolicy rateLimitPolicy, User user, Instant expiresAt) {
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.name = name;
        this.rateLimitPolicy = rateLimitPolicy;
        this.user = user;
        this.expiresAt = expiresAt;
        this.status = ApiKeyStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isValid() {
        if (status != ApiKeyStatus.ACTIVE) {
            return false;
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        return rateLimitPolicy != null && Boolean.TRUE.equals(rateLimitPolicy.getIsActive());
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ApiKeyStatus getStatus() { return status; }
    public void setStatus(ApiKeyStatus status) { this.status = status; }

    public RateLimitPolicy getRateLimitPolicy() { return rateLimitPolicy; }
    public void setRateLimitPolicy(RateLimitPolicy rateLimitPolicy) { this.rateLimitPolicy = rateLimitPolicy; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiKey apiKey)) return false;
        return Objects.equals(id, apiKey.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
