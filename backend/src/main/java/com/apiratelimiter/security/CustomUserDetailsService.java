package com.apiratelimiter.security;

import com.apiratelimiter.entity.User;
import com.apiratelimiter.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            throw new UsernameNotFoundException("Username or email cannot be empty");
        }
        String identifier = usernameOrEmail.trim();
        User user = userRepository.findByUsernameIgnoreCase(identifier)
            .or(() -> userRepository.findByEmailIgnoreCase(identifier))
            .orElseThrow(() -> new UsernameNotFoundException("User not found with username or email: " + identifier));

        return UserPrincipal.create(user);
    }
}
