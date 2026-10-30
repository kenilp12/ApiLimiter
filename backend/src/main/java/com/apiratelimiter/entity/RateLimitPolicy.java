package com.apiratelimiter.entity;

import com.apiratelimiter.enums.AlgorithmType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "rate_limit_policies")
public class RateLimitPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AlgorithmType algorithm;

    @Column(name = "limit_value", nullable = false)
    private Long limitValue;

    @Column(name = "time_window_seconds", nullable = false)
    private Long timeWindowSeconds;

    @Column(name = "burst_capacity")
    private Long burstCapacity;

    @Column(name = "refill_rate")
    private Double refillRate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public RateLimitPolicy() {}

    public RateLimitPolicy(String name, String description, AlgorithmType algorithm,
                           Long limitValue, Long timeWindowSeconds, Long burstCapacity,
                           Double refillRate, Boolean isActive) {
        this.name = name;
        this.description = description;
        this.algorithm = algorithm;
        this.limitValue = limitValue;
        this.timeWindowSeconds = timeWindowSeconds;
        this.burstCapacity = burstCapacity;
        this.refillRate = refillRate;
        this.isActive = isActive != null ? isActive : true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public AlgorithmType getAlgorithm() { return algorithm; }
    public void setAlgorithm(AlgorithmType algorithm) { this.algorithm = algorithm; }

    public Long getLimitValue() { return limitValue; }
    public void setLimitValue(Long limitValue) { this.limitValue = limitValue; }

    public Long getTimeWindowSeconds() { return timeWindowSeconds; }
    public void setTimeWindowSeconds(Long timeWindowSeconds) { this.timeWindowSeconds = timeWindowSeconds; }

    public Long getBurstCapacity() { return burstCapacity; }
    public void setBurstCapacity(Long burstCapacity) { this.burstCapacity = burstCapacity; }

    public Double getRefillRate() { return refillRate; }
    public void setRefillRate(Double refillRate) { this.refillRate = refillRate; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RateLimitPolicy that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
