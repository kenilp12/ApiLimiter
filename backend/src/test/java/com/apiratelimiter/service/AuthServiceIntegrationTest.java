package com.apiratelimiter.service;

import com.apiratelimiter.dto.AuthRequest;
import com.apiratelimiter.dto.AuthResponse;
import com.apiratelimiter.dto.RegisterRequest;
import com.apiratelimiter.dto.UserResponse;
import com.apiratelimiter.entity.User;
import com.apiratelimiter.enums.Role;
import com.apiratelimiter.repository.ApiKeyRepository;
import com.apiratelimiter.repository.RequestLogRepository;
import com.apiratelimiter.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private RequestLogRepository requestLogRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        requestLogRepository.deleteAll();
        apiKeyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testRegisterAndLogin() {
        RegisterRequest registerReq = new RegisterRequest("newuser", "newuser@example.com", "password123");
        UserResponse registered = authService.register(registerReq);

        assertNotNull(registered);
        assertEquals("newuser", registered.username());
        assertEquals("newuser@example.com", registered.email());

        // Now test login with username
        AuthRequest loginReq = new AuthRequest("newuser", "password123");
        AuthResponse loginRes = authService.login(loginReq);

        assertNotNull(loginRes);
        assertNotNull(loginRes.token());
        assertEquals("newuser", loginRes.user().username());

        // Now test login with email
        AuthRequest loginEmailReq = new AuthRequest("newuser@example.com", "password123");
        AuthResponse loginEmailRes = authService.login(loginEmailReq);

        assertNotNull(loginEmailRes);
        assertNotNull(loginEmailRes.token());
        assertEquals("newuser", loginEmailRes.user().username());

        // Now test case-insensitive username login
        AuthRequest loginCaseReq = new AuthRequest("NewUser", "password123");
        AuthResponse loginCaseRes = authService.login(loginCaseReq);
        assertNotNull(loginCaseRes);
        assertEquals("newuser", loginCaseRes.user().username());

        // Now test case-insensitive email login
        AuthRequest loginEmailCaseReq = new AuthRequest("NewUser@Example.COM", "password123");
        AuthResponse loginEmailCaseRes = authService.login(loginEmailCaseReq);
        assertNotNull(loginEmailCaseRes);
        assertEquals("newuser", loginEmailCaseRes.user().username());
    }

    @Test
    void testLoginWithWrongPasswordThrowsBadCredentials() {
        RegisterRequest registerReq = new RegisterRequest("user2", "user2@example.com", "password123");
        authService.register(registerReq);

        AuthRequest wrongPass = new AuthRequest("user2", "wrongpassword");
        assertThrows(BadCredentialsException.class, () -> authService.login(wrongPass));
    }
}

