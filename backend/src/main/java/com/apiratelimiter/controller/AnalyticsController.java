package com.apiratelimiter.controller;

import com.apiratelimiter.dto.AnalyticsOverviewResponse;
import com.apiratelimiter.dto.RequestLogResponse;
import com.apiratelimiter.dto.TimeseriesPoint;
import com.apiratelimiter.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Real-time metrics, traffic time-series, and audit logs")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Get platform metrics overview (total, allowed, blocked, latency, algorithm breakdown)")
    public ResponseEntity<AnalyticsOverviewResponse> getOverview() {
        AnalyticsOverviewResponse overview = analyticsService.getOverview();
        return ResponseEntity.ok(overview);
    }

    @GetMapping("/requests")
    @Operation(summary = "Get paginated recent API requests")
    public ResponseEntity<Page<RequestLogResponse>> getRecentRequests(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(100, Math.max(1, size)));
        Page<RequestLogResponse> requests = analyticsService.getRecentRequests(pageable);
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/blocked")
    @Operation(summary = "Get paginated rate-limit blocked requests (HTTP 429) with optional filters")
    public ResponseEntity<Page<RequestLogResponse>> getBlockedRequests(
        @RequestParam(required = false) String endpoint,
        @RequestParam(required = false) String algorithm,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RequestLogResponse> blocked = analyticsService.getBlockedRequests(endpoint, algorithm, search, fromDate, toDate, pageable);
        return ResponseEntity.ok(blocked);
    }

    @GetMapping("/timeseries")
    @Operation(summary = "Get time-series data for dashboard charts")
    public ResponseEntity<List<TimeseriesPoint>> getTimeseries(
        @RequestParam(defaultValue = "60") int minutes
    ) {
        List<TimeseriesPoint> points = analyticsService.getTimeseries(minutes);
        return ResponseEntity.ok(points);
    }
}
