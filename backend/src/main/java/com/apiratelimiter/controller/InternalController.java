package com.apiratelimiter.controller;

import com.apiratelimiter.dto.ApiKeyValidationResponse;
import com.apiratelimiter.dto.LogIngestRequest;
import com.apiratelimiter.service.ApiKeyService;
import com.apiratelimiter.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/internal")
@Tag(name = "Internal Service API", description = "Endpoints for Gateway service to validate keys and push traffic logs")
public class InternalController {

    private final ApiKeyService apiKeyService;
    private final AnalyticsService analyticsService;

    public InternalController(ApiKeyService apiKeyService, AnalyticsService analyticsService) {
        this.apiKeyService = apiKeyService;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/keys/resolve")
    @Operation(summary = "Resolve API key policy details by SHA-256 hash")
    public ResponseEntity<ApiKeyValidationResponse> resolveKey(@RequestParam("keyHash") String keyHash) {
        ApiKeyValidationResponse response = apiKeyService.resolveKeyByHash(keyHash);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logs")
    @Operation(summary = "Batch ingest request logs from Gateway")
    public ResponseEntity<Map<String, Object>> ingestLogs(@RequestBody LogIngestRequest request) {
        analyticsService.ingestBatchLogs(request);
        return ResponseEntity.ok(Map.of("status", "success", "count", request.entries() != null ? request.entries().size() : 0));
    }
}
