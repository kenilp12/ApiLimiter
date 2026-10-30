package com.apiratelimiter.dto;

public record ApiKeyCreatedResponse(
    ApiKeyResponse apiKey,
    String rawApiKey,
    String warning
) {
    public ApiKeyCreatedResponse(ApiKeyResponse apiKey, String rawApiKey) {
        this(apiKey, rawApiKey, "Please copy and save your secret API key immediately. You will not be able to see it again.");
    }
}
