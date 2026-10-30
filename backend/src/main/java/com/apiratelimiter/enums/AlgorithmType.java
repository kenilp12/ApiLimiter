package com.apiratelimiter.enums;

public enum AlgorithmType {
    FIXED_WINDOW("Fixed Window Counter"),
    SLIDING_WINDOW("Sliding Window Log"),
    TOKEN_BUCKET("Token Bucket"),
    LEAKY_BUCKET("Leaky Bucket");

    private final String displayName;

    AlgorithmType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
