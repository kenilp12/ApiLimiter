package com.apiratelimiter.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String jwtSchemeName = "BearerAuth";
        final String apiKeySchemeName = "ApiKeyAuth";

        return new OpenAPI()
            .info(new Info()
                .title("API Rate Limiter — Distributed Platform API")
                .version("1.0.0")
                .description("Production-grade distributed API rate limiting platform with Redis Lua scripts, token bucket, sliding window, fixed window, and leaky bucket algorithms.")
                .contact(new Contact().name("API Rate Limiter Engineering Team").email("support@apiratelimiter.local"))
                .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
            .addSecurityItem(new SecurityRequirement().addList(jwtSchemeName))
            .components(new Components()
                .addSecuritySchemes(jwtSchemeName,
                    new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes(apiKeySchemeName,
                    new SecurityScheme()
                        .name("X-API-Key")
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)));
    }
}
