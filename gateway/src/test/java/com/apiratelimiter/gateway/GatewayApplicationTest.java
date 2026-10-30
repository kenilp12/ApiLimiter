package com.apiratelimiter.gateway;

import com.apiratelimiter.gateway.model.ApiKeyPolicyInfo;
import com.apiratelimiter.gateway.service.KeyResolutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private KeyResolutionService keyResolutionService;

    @Test
    @DisplayName("Context loads and Gateway context initializes successfully")
    void contextLoads() {
        assertNotNull(context);
    }

    @Test
    @DisplayName("Gateway rejects protected request without API key with 401 Unauthorized")
    void testMissingApiKeyReturns401() {
        webTestClient.get()
            .uri("/protected-api/data")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().valueEquals("Content-Type", "application/json")
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.error").isEqualTo("Missing API Key");
    }

    @Test
    @DisplayName("Gateway rejects invalid API key with 403 Forbidden")
    void testInvalidApiKeyReturns403() {
        when(keyResolutionService.resolveKey(anyString()))
            .thenReturn(Mono.just(ApiKeyPolicyInfo.invalid("Revoked key")));

        webTestClient.get()
            .uri("/protected-api/data")
            .header("X-API-Key", "arl_live_invalidkey123456789")
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.status").isEqualTo(403)
            .jsonPath("$.error").isEqualTo("Invalid API Key");
    }
}
