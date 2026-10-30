package com.apiratelimiter.controller;

import com.apiratelimiter.dto.PolicyCreateRequest;
import com.apiratelimiter.dto.PolicyResponse;
import com.apiratelimiter.dto.PolicyUpdateRequest;
import com.apiratelimiter.security.UserPrincipal;
import com.apiratelimiter.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policies")
@Tag(name = "Rate Limit Policies", description = "Endpoints for configuring and managing rate-limiting algorithms and rules")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new rate limit policy (Admin only)")
    public ResponseEntity<PolicyResponse> createPolicy(
        @Valid @RequestBody PolicyCreateRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        PolicyResponse response = policyService.createPolicy(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all rate limit policies")
    public ResponseEntity<List<PolicyResponse>> listPolicies() {
        List<PolicyResponse> policies = policyService.listPolicies();
        return ResponseEntity.ok(policies);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get rate limit policy by ID")
    public ResponseEntity<PolicyResponse> getPolicy(@PathVariable("id") Long id) {
        PolicyResponse response = policyService.getPolicy(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an existing rate limit policy (Admin only)")
    public ResponseEntity<PolicyResponse> updatePolicy(
        @PathVariable("id") Long id,
        @Valid @RequestBody PolicyUpdateRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        PolicyResponse response = policyService.updatePolicy(id, request, principal);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a rate limit policy (Admin only)")
    public ResponseEntity<Map<String, String>> deletePolicy(
        @PathVariable("id") Long id,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        policyService.deletePolicy(id, principal);
        return ResponseEntity.ok(Map.of("message", "Policy deleted successfully", "id", id.toString()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or deactivate a rate limit policy (Admin only)")
    public ResponseEntity<PolicyResponse> toggleStatus(
        @PathVariable("id") Long id,
        @RequestParam("active") boolean active,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        PolicyResponse response = policyService.toggleStatus(id, active, principal);
        return ResponseEntity.ok(response);
    }
}
