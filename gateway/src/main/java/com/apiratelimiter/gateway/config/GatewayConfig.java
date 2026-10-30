package com.apiratelimiter.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GatewayConfig {

    @Value("${ratelimiter.backend-url:http://localhost:8080}")
    private String backendUrl;

    @Bean
    public WebClient backendWebClient() {
        return WebClient.builder()
            .baseUrl(backendUrl)
            .build();
    }
}
