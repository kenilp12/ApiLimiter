package com.apiratelimiter.service;

import com.apiratelimiter.dto.AuthRequest;
import com.apiratelimiter.dto.AuthResponse;
import com.apiratelimiter.dto.RegisterRequest;
import com.apiratelimiter.dto.UserResponse;
import com.apiratelimiter.entity.User;
import com.apiratelimiter.enums.Role;
import com.apiratelimiter.exception.BadRequestException;
import com.apiratelimiter.exception.ResourceNotFoundException;
import com.apiratelimiter.repository.UserRepository;
import com.apiratelimiter.security.JwtTokenProvider;
import com.apiratelimiter.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final AuditService auditService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtTokenProvider tokenProvider,
        AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.auditService = auditService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String cleanUsername = request.username().trim();
        String cleanEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByUsername(cleanUsername)) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByEmail(cleanEmail)) {
            throw new BadRequestException("Email address is already in use");
        }

        // If no users exist, make the first user an ADMIN
        Role role = userRepository.count() == 0 ? Role.ROLE_ADMIN : Role.ROLE_USER;

        User user = new User(
            cleanUsername,
            cleanEmail,
            passwordEncoder.encode(request.password()),
            role
        );

        User saved = userRepository.save(user);
        auditService.record("REGISTER", "USER", saved.getId().toString(), saved.getUsername(), "User registered with role " + role);
        return UserResponse.fromEntity(saved);
    }

    public AuthResponse login(AuthRequest request) {
        String identifier = request.username() != null ? request.username().trim() : "";
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(identifier, request.password())
        );

        String jwt = tokenProvider.generateToken(authentication);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        auditService.record("LOGIN", "USER", user.getId().toString(), user.getUsername(), "Successful login");
        return new AuthResponse(jwt, tokenProvider.getExpirationMs(), UserResponse.fromEntity(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UserPrincipal principal) {
        if (principal == null) {
            throw new BadRequestException("No authenticated user");
        }
        User user = userRepository.findById(principal.getId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));
        return UserResponse.fromEntity(user);
    }
}
