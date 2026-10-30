package com.apiratelimiter.service;

import com.apiratelimiter.dto.AnalyticsOverviewResponse;
import com.apiratelimiter.dto.RequestLogResponse;
import com.apiratelimiter.entity.RequestLog;
import com.apiratelimiter.repository.ApiKeyRepository;
import com.apiratelimiter.repository.RateLimitPolicyRepository;
import com.apiratelimiter.repository.RequestLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AnalyticsServiceTest {

    @Mock private RequestLogRepository requestLogRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private RateLimitPolicyRepository policyRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(requestLogRepository, apiKeyRepository, policyRepository);
    }

    @Test
    @DisplayName("Overview calculates total = allowed + blocked and correct rejection percentage")
    void testOverviewCalculation() {
        when(requestLogRepository.countByIsAllowed(true)).thenReturn(80L);
        when(requestLogRepository.countByIsAllowed(false)).thenReturn(20L);
        when(requestLogRepository.getAverageResponseTimeAfter(any(Instant.class))).thenReturn(15.4);
        when(apiKeyRepository.findByStatus(any())).thenReturn(Collections.emptyList());
        when(policyRepository.findByIsActiveTrue()).thenReturn(Collections.emptyList());
        when(requestLogRepository.countByAlgorithm()).thenReturn(List.of(
            new Object[]{"TOKEN_BUCKET", 60L},
            new Object[]{"FIXED_WINDOW", 40L}
        ));

        AnalyticsOverviewResponse res = analyticsService.getOverview();

        assertEquals(100L, res.totalRequests());
        assertEquals(80L, res.allowedRequests());
        assertEquals(20L, res.blockedRequests());
        assertEquals(20.0, res.rejectionRatePercentage());
        assertEquals(15.4, res.averageResponseTimeMs());
        assertEquals(60L, res.algorithmBreakdown().get("TOKEN_BUCKET"));
    }

    @Test
    @DisplayName("Get blocked requests with filters queries specification")
    void testGetBlockedRequestsWithFilters() {
        RequestLog logEntry = new RequestLog(
            "arl_live_123...", "/protected-api/data", "GET", "127.0.0.1", 429, false, 10L, 0L, "TOKEN_BUCKET", "Standard Policy", 5L
        );
        Page<RequestLog> page = new PageImpl<>(List.of(logEntry));
        when(requestLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Pageable pageable = PageRequest.of(0, 10);
        Page<RequestLogResponse> result = analyticsService.getBlockedRequests("/protected-api/data", "TOKEN_BUCKET", null, null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(429, result.getContent().get(0).statusCode());
        assertFalse(result.getContent().get(0).isAllowed());
        assertEquals("Standard Policy", result.getContent().get(0).policyName());
    }
}

