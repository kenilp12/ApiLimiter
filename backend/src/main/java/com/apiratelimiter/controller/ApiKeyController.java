package com.apiratelimiter.controller;

import com.apiratelimiter.dto.ApiKeyCreateRequest;
import com.apiratelimiter.dto.ApiKeyCreatedResponse;
import com.apiratelimiter.dto.ApiKeyResponse;
import com.apiratelimiter.security.UserPrincipal;
import com.apiratelimiter.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/keys")
@Tag(name = "API Keys", description = "Endpoints for creating, listing, and revoking API keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @Operation(summary = "Generate a new API key (secret key displayed only once)")
    public ResponseEntity<ApiKeyCreatedResponse> createKey(
        @Valid @RequestBody ApiKeyCreateRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        ApiKeyCreatedResponse response = apiKeyService.createApiKey(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all API keys belonging to user (or all if admin)")
    public ResponseEntity<List<ApiKeyResponse>> listKeys(@AuthenticationPrincipal UserPrincipal principal) {
        List<ApiKeyResponse> keys = apiKeyService.listKeys(principal);
        return ResponseEntity.ok(keys);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke an API key")
    public ResponseEntity<Map<String, String>> revokeKey(
        @PathVariable("id") Long id,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        apiKeyService.revokeKey(id, principal);
        return ResponseEntity.ok(Map.of("message", "API key revoked successfully", "id", id.toString()));
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Revoke an API key (POST alias)")
    public ResponseEntity<Map<String, String>> revokeKeyPost(
        @PathVariable("id") Long id,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        apiKeyService.revokeKey(id, principal);
        return ResponseEntity.ok(Map.of("message", "API key revoked successfully", "id", id.toString()));
    }
}
