package com.apiratelimiter.controller;

import com.apiratelimiter.dto.AuthRequest;
import com.apiratelimiter.dto.AuthResponse;
import com.apiratelimiter.dto.RegisterRequest;
import com.apiratelimiter.dto.UserResponse;
import com.apiratelimiter.enums.Role;
import com.apiratelimiter.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;

    @Test
    @DisplayName("POST /api/auth/register should return 201 Created on valid input")
    void testRegisterEndpoint() throws Exception {
        RegisterRequest req = new RegisterRequest("testuser", "test@test.com", "validPass123");
        UserResponse response = new UserResponse(1L, "testuser", "test@test.com", Role.ROLE_USER, Instant.now());

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("testuser"))
            .andExpect(jsonPath("$.email").value("test@test.com"));
    }

    @Test
    @DisplayName("POST /api/auth/login should return 200 OK with JWT token")
    void testLoginEndpoint() throws Exception {
        AuthRequest req = new AuthRequest("testuser", "validPass123");
        UserResponse user = new UserResponse(1L, "testuser", "test@test.com", Role.ROLE_USER, Instant.now());
        AuthResponse response = new AuthResponse("mock.jwt.token", 3600000L, user);

        when(authService.login(any(AuthRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("mock.jwt.token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }
}
