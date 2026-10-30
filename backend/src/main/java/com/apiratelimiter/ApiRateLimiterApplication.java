package com.apiratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ApiRateLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiRateLimiterApplication.class, args);
    }
}
