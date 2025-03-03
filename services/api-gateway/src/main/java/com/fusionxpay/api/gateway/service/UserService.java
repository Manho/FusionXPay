package com.fusionxpay.api.gateway.service;

import com.fusionxpay.api.gateway.dto.AuthRequest;
import com.fusionxpay.api.gateway.dto.AuthResponse;
import com.fusionxpay.api.gateway.model.User;
import com.fusionxpay.api.gateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(AuthRequest request) throws Exception {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new Exception("User already exists");
        }
        // Hash the password using BCrypt
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User user = User.builder()
                .username(request.getUsername())
                .password(hashedPassword)
                .apiKey(UUID.randomUUID().toString())
                .roles("USER")
                .build();
        User savedUser = userRepository.save(user);
        log.info("Registered user: {}", savedUser.getUsername());
        return AuthResponse.builder()
                .username(savedUser.getUsername())
                .apiKey(savedUser.getApiKey())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) throws Exception {
        Optional<User> optionalUser = userRepository.findByUsername(request.getUsername());
        if (optionalUser.isEmpty()) {
            throw new Exception("Invalid credentials");
        }
        User user = optionalUser.get();
        // Verify the password using BCrypt
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new Exception("Invalid credentials");
        }
        log.info("User logged in: {}", user.getUsername());
        return AuthResponse.builder()
                .username(user.getUsername())
                .apiKey(user.getApiKey())
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserByApiKey(String apiKey) {
        return userRepository.findByApiKey(apiKey);
    }
}
