package com.apiratelimiter.repository;

import com.apiratelimiter.entity.RequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, Long>, JpaSpecificationExecutor<RequestLog> {

    Page<RequestLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<RequestLog> findByIsAllowedFalseOrderByCreatedAtDesc(Pageable pageable);

    long countByIsAllowed(boolean isAllowed);

    long countByCreatedAtAfter(Instant after);

    long countByCreatedAtAfterAndIsAllowed(Instant after, boolean isAllowed);

    List<RequestLog> findByCreatedAtAfterOrderByCreatedAtAsc(Instant after);

    @Query("SELECT r.algorithm, COUNT(r) FROM RequestLog r WHERE r.algorithm IS NOT NULL GROUP BY r.algorithm")
    List<Object[]> countByAlgorithm();

    @Query("SELECT AVG(r.responseTimeMs) FROM RequestLog r WHERE r.createdAt >= :after")
    Double getAverageResponseTimeAfter(Instant after);
}
