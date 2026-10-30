package com.apiratelimiter.service;

import com.apiratelimiter.dto.AnalyticsOverviewResponse;
import com.apiratelimiter.dto.LogIngestRequest;
import com.apiratelimiter.dto.RequestLogResponse;
import com.apiratelimiter.dto.TimeseriesPoint;
import com.apiratelimiter.entity.RequestLog;
import com.apiratelimiter.enums.ApiKeyStatus;
import com.apiratelimiter.repository.ApiKeyRepository;
import com.apiratelimiter.repository.RateLimitPolicyRepository;
import com.apiratelimiter.repository.RequestLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AnalyticsService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final RequestLogRepository requestLogRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final RateLimitPolicyRepository policyRepository;

    public AnalyticsService(
        RequestLogRepository requestLogRepository,
        ApiKeyRepository apiKeyRepository,
        RateLimitPolicyRepository policyRepository
    ) {
        this.requestLogRepository = requestLogRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.policyRepository = policyRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getOverview() {
        long allowed = requestLogRepository.countByIsAllowed(true);
        long blocked = requestLogRepository.countByIsAllowed(false);
        long total = allowed + blocked;

        double rejectionRate = total > 0 ? ((double) blocked / total) * 100.0 : 0.0;
        Instant pastHour = Instant.now().minus(1, ChronoUnit.HOURS);
        Double avgLatency = requestLogRepository.getAverageResponseTimeAfter(pastHour);
        if (avgLatency == null) avgLatency = 0.0;

        long activeKeys = apiKeyRepository.findByStatus(ApiKeyStatus.ACTIVE).size();
        long activePolicies = policyRepository.findByIsActiveTrue().size();

        Map<String, Long> algorithmBreakdown = new HashMap<>();
        for (Object[] row : requestLogRepository.countByAlgorithm()) {
            if (row[0] != null && row[1] != null) {
                algorithmBreakdown.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        return new AnalyticsOverviewResponse(
            total,
            allowed,
            blocked,
            Math.round(rejectionRate * 100.0) / 100.0,
            Math.round(avgLatency * 10.0) / 10.0,
            activeKeys,
            activePolicies,
            algorithmBreakdown
        );
    }

    @Transactional(readOnly = true)
    public Page<RequestLogResponse> getRecentRequests(Pageable pageable) {
        return requestLogRepository.findAllByOrderByCreatedAtDesc(pageable)
            .map(RequestLogResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<RequestLogResponse> getBlockedRequests(Pageable pageable) {
        return getBlockedRequests(null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<RequestLogResponse> getBlockedRequests(
            String endpoint,
            String algorithm,
            String search,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    ) {
        Specification<RequestLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isAllowed"), false));

            if (endpoint != null && !endpoint.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("endpoint")), "%" + endpoint.trim().toLowerCase() + "%"));
            }
            if (algorithm != null && !algorithm.isBlank() && !"ALL".equalsIgnoreCase(algorithm)) {
                predicates.add(cb.equal(root.get("algorithm"), algorithm.trim().toUpperCase()));
            }
            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate endpointMatch = cb.like(cb.lower(root.get("endpoint")), searchPattern);
                Predicate prefixMatch = cb.like(cb.lower(root.get("apiKeyPrefix")), searchPattern);
                predicates.add(cb.or(endpointMatch, prefixMatch));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return requestLogRepository.findAll(spec, pageable).map(RequestLogResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<TimeseriesPoint> getTimeseries(int minutes) {
        int windowMinutes = minutes > 0 ? minutes : 60;
        Instant start = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES);
        List<RequestLog> logs = requestLogRepository.findByCreatedAtAfterOrderByCreatedAtAsc(start);

        // Group by 5-minute or 1-minute bucket
        Map<String, long[]> buckets = new LinkedHashMap<>();
        Instant current = start.truncatedTo(ChronoUnit.MINUTES);
        Instant now = Instant.now();

        int stepMinutes = Math.max(1, windowMinutes / 12);
        while (!current.isAfter(now)) {
            String label = TIME_FORMATTER.format(current);
            buckets.put(label, new long[]{0, 0, 0}); // total, allowed, blocked
            current = current.plus(stepMinutes, ChronoUnit.MINUTES);
        }

        for (RequestLog r : logs) {
            String label = TIME_FORMATTER.format(r.getCreatedAt());
            if (buckets.containsKey(label)) {
                long[] counts = buckets.get(label);
                counts[0]++;
                if (Boolean.TRUE.equals(r.getIsAllowed())) {
                    counts[1]++;
                } else {
                    counts[2]++;
                }
            }
        }

        List<TimeseriesPoint> points = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : buckets.entrySet()) {
            long[] counts = entry.getValue();
            points.add(new TimeseriesPoint(entry.getKey(), counts[0], counts[1], counts[2]));
        }

        return points;
    }

    @Transactional
    public void recordLog(RequestLog logEntry) {
        requestLogRepository.save(logEntry);
    }

    @Transactional
    public void ingestBatchLogs(LogIngestRequest request) {
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            return;
        }

        List<RequestLog> list = request.entries().stream().map(e ->
            new RequestLog(
                e.apiKeyPrefix(),
                e.endpoint(),
                e.method(),
                e.clientIp(),
                e.statusCode(),
                e.isAllowed(),
                e.rateLimitLimit(),
                e.rateLimitRemaining(),
                e.algorithm(),
                e.policyName(),
                e.responseTimeMs()
            )
        ).toList();

        requestLogRepository.saveAll(list);
    }
}
