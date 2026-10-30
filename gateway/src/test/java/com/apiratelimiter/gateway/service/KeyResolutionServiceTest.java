package com.apiratelimiter.gateway.service;

import com.apiratelimiter.gateway.model.ApiKeyPolicyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "rawtypes", "unchecked" })
class KeyResolutionServiceTest {

    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    private KeyResolutionService keyResolutionService;

    @BeforeEach
    void setUp() {
        keyResolutionService = new KeyResolutionService(webClient);
    }

    @Test
    @DisplayName("Invalidate removes key from local memory cache")
    @SuppressWarnings("unchecked")
    void testInvalidateClearsKey() {
        ApiKeyPolicyInfo info = new ApiKeyPolicyInfo(
                true, "arl_live_test...", 1L, "Policy 1", "FIXED_WINDOW", 5L, 10L, 5L, 0.5, null);

        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri(any(Function.class));
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        doReturn(Mono.just(info)).when(responseSpec).bodyToMono(ApiKeyPolicyInfo.class);

        // First call populates cache
        StepVerifier.create(keyResolutionService.resolveKey("hash_1"))
                .expectNextMatches(p -> p.valid() && "FIXED_WINDOW".equals(p.algorithm()))
                .verifyComplete();

        // Invalidate specific key
        keyResolutionService.invalidate("hash_1");

        // Subsequent call must query backend again
        StepVerifier.create(keyResolutionService.resolveKey("hash_1"))
                .expectNextMatches(p -> p.valid() && "FIXED_WINDOW".equals(p.algorithm()))
                .verifyComplete();

        verify(webClient, times(2)).get();
    }
}
