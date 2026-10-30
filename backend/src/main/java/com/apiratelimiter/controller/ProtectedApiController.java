package com.apiratelimiter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/protected-api")
@Tag(name = "Protected Downstream Endpoints", description = "Mock business APIs routed through the distributed rate limiting gateway")
public class ProtectedApiController {

    @GetMapping("/data")
    @Operation(summary = "Sample high-value analytics data endpoint")
    public ResponseEntity<Map<String, Object>> getData() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Protected data payload retrieved successfully",
            "timestamp", Instant.now().toString(),
            "data", Map.of(
                "activeConnections", 1420,
                "cacheHitRatio", 0.942,
                "latencyP99Ms", 18.4
            )
        ));
    }

    @GetMapping("/users")
    @Operation(summary = "Sample user directory endpoint")
    public ResponseEntity<Map<String, Object>> getUsers() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "total", 3,
            "users", List.of(
                Map.of("id", 101, "name", "Alice Chen", "tier", "ENTERPRISE"),
                Map.of("id", 102, "name", "Bob Miller", "tier", "DEVELOPER"),
                Map.of("id", 103, "name", "Carol Danvers", "tier", "PRO")
            )
        ));
    }

    @GetMapping("/orders")
    @Operation(summary = "Sample orders feed endpoint")
    public ResponseEntity<Map<String, Object>> getOrders() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "orders", List.of(
                Map.of("orderId", "ORD-9901", "amount", 450.00, "status", "FULFILLED"),
                Map.of("orderId", "ORD-9902", "amount", 1290.50, "status", "PROCESSING")
            )
        ));
    }

    @PostMapping("/echo")
    @Operation(summary = "Echoes request payload back to client")
    public ResponseEntity<Map<String, Object>> echo(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "echo", body != null ? body : Map.of(),
            "timestamp", Instant.now().toString()
        ));
    }
}
