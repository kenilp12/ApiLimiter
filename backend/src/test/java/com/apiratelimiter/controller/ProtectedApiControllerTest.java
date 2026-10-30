package com.apiratelimiter.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProtectedApiControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("GET /protected-api/data should return mock analytics payload")
    void testProtectedData() throws Exception {
        mockMvc.perform(get("/protected-api/data"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.data.activeConnections").value(1420));
    }

    @Test
    @DisplayName("GET /protected-api/users should return sample users")
    void testProtectedUsers() throws Exception {
        mockMvc.perform(get("/protected-api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    @DisplayName("POST /protected-api/echo should echo request payload")
    void testProtectedEcho() throws Exception {
        mockMvc.perform(post("/protected-api/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hello rate limiter\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.echo.message").value("hello rate limiter"));
    }
}
