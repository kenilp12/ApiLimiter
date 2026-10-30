package com.apiratelimiter.repository;

import com.apiratelimiter.entity.RateLimitPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RateLimitPolicyRepository extends JpaRepository<RateLimitPolicy, Long> {
    Optional<RateLimitPolicy> findByName(String name);
    List<RateLimitPolicy> findByIsActiveTrue();
}
