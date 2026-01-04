package com.fusionxpay.api.gateway.service;

import com.fusionxpay.api.gateway.dto.AuthRequest;
import com.fusionxpay.api.gateway.dto.AuthResponse;
import com.fusionxpay.api.gateway.model.User;
import com.fusionxpay.api.gateway.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> cleanupUsernames = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String username : cleanupUsernames) {
            userRepository.findByUsername(username).ifPresent(userRepository::delete);
        }
        cleanupUsernames.clear();
    }

    @Test
    @DisplayName("Register creates a new user with hashed password")
    void register_Success() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setUsername("user-" + UUID.randomUUID());
        authRequest.setPassword("testpassword");
        cleanupUsernames.add(authRequest.getUsername());

        AuthResponse response = userService.register(authRequest);

        assertNotNull(response);
        assertEquals(authRequest.getUsername(), response.getUsername());
        assertNotNull(response.getApiKey());

        User savedUser = userRepository.findByUsername(authRequest.getUsername()).orElseThrow();
        assertTrue(passwordEncoder.matches(authRequest.getPassword(), savedUser.getPassword()));
    }

    @Test
    @DisplayName("Register fails for duplicate user")
    void register_DuplicateUser() {
        String username = "user-" + UUID.randomUUID();
        cleanupUsernames.add(username);

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode("testpassword"))
                .apiKey(UUID.randomUUID().toString())
                .roles("USER")
                .build();
        userRepository.save(user);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setUsername(username);
        authRequest.setPassword("testpassword");

        Exception exception = assertThrows(Exception.class, () -> userService.register(authRequest));
        assertTrue(exception.getMessage().contains("User already exists"));
    }

    @Test
    @DisplayName("Login succeeds with valid credentials")
    void login_Success() throws Exception {
        String username = "user-" + UUID.randomUUID();
        String password = "testpassword";
        String apiKey = UUID.randomUUID().toString();
        cleanupUsernames.add(username);

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .apiKey(apiKey)
                .roles("USER")
                .build();
        userRepository.save(user);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setUsername(username);
        authRequest.setPassword(password);

        AuthResponse response = userService.login(authRequest);

        assertNotNull(response);
        assertEquals(username, response.getUsername());
        assertEquals(apiKey, response.getApiKey());
    }

    @Test
    @DisplayName("Login fails with invalid credentials")
    void login_Failure_WrongPassword() {
        String username = "user-" + UUID.randomUUID();
        cleanupUsernames.add(username);

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode("testpassword"))
                .apiKey(UUID.randomUUID().toString())
                .roles("USER")
                .build();
        userRepository.save(user);

        AuthRequest authRequest = new AuthRequest();
        authRequest.setUsername(username);
        authRequest.setPassword("wrong-password");

        Exception exception = assertThrows(Exception.class, () -> userService.login(authRequest));
        assertTrue(exception.getMessage().contains("Invalid credentials"));
    }

    @Test
    @DisplayName("Get user by API key returns user")
    void getUserByApiKey_Found() {
        String username = "user-" + UUID.randomUUID();
        String apiKey = UUID.randomUUID().toString();
        cleanupUsernames.add(username);

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode("testpassword"))
                .apiKey(apiKey)
                .roles("USER")
                .build();
        userRepository.save(user);

        Optional<User> result = userService.getUserByApiKey(apiKey);

        assertTrue(result.isPresent());
        assertEquals(username, result.get().getUsername());
    }

    @Test
    @DisplayName("Get user by API key returns empty when not found")
    void getUserByApiKey_NotFound() {
        Optional<User> result = userService.getUserByApiKey("nonexistent-api-key");
        assertFalse(result.isPresent());
    }
}
