package com.apiratelimiter.repository;

import com.apiratelimiter.entity.ApiKey;
import com.apiratelimiter.enums.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyHash(String keyHash);
    List<ApiKey> findByUserId(Long userId);
    List<ApiKey> findByStatus(ApiKeyStatus status);
}
