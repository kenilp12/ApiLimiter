package com.apiratelimiter.service;

import com.apiratelimiter.dto.RegisterRequest;
import com.apiratelimiter.dto.UserResponse;
import com.apiratelimiter.entity.User;
import com.apiratelimiter.enums.Role;
import com.apiratelimiter.exception.BadRequestException;
import com.apiratelimiter.repository.UserRepository;
import com.apiratelimiter.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private AuditService auditService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, tokenProvider, auditService);
    }

    @Test
    @DisplayName("Should successfully register a new user")
    void testRegisterSuccess() {
        RegisterRequest req = new RegisterRequest("newuser", "new@example.com", "secretpass");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secretpass")).thenReturn("encoded_pass");
        when(userRepository.count()).thenReturn(1L);

        User savedUser = new User("newuser", "new@example.com", "encoded_pass", Role.ROLE_USER);
        savedUser.setId(10L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse res = authService.register(req);
        assertNotNull(res);
        assertEquals("newuser", res.username());
        assertEquals("new@example.com", res.email());
        assertEquals(Role.ROLE_USER, res.role());
    }

    @Test
    @DisplayName("Should reject duplicate username")
    void testRegisterDuplicateUsername() {
        RegisterRequest req = new RegisterRequest("existinguser", "unique@example.com", "password");
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(req));
    }
}
