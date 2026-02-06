package com.fusionxpay.api.gateway.controller;

import com.fusionxpay.api.gateway.dto.AuthRequest;
import com.fusionxpay.api.gateway.model.User;
import com.fusionxpay.api.gateway.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

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
    @DisplayName("Register succeeds and persists user")
    void registerSuccess() {
        String username = "user-" + UUID.randomUUID();
        String password = "testpassword";
        cleanupUsernames.add(username);

        AuthRequest request = new AuthRequest();
        request.setUsername(username);
        request.setPassword(password);

        webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.username").isEqualTo(username)
                .jsonPath("$.apiKey").isNotEmpty();
        assertTrue(userRepository.findByUsername(username)
                .map(user -> passwordEncoder.matches(password, user.getPassword()))
                .orElse(false));
    }

    @Test
    @DisplayName("Register returns 400 for duplicate user")
    void registerDuplicateUsername() {
        String username = "user-" + UUID.randomUUID();
        String password = "testpassword";
        cleanupUsernames.add(username);

        User existingUser = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .apiKey(UUID.randomUUID().toString())
                .roles("USER")
                .build();
        userRepository.save(existingUser);

        AuthRequest request = new AuthRequest();
        request.setUsername(username);
        request.setPassword(password);

        webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Login succeeds with valid credentials")
    void loginSuccess() {
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

        AuthRequest request = new AuthRequest();
        request.setUsername(username);
        request.setPassword(password);

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo(username)
                .jsonPath("$.apiKey").isEqualTo(apiKey);
    }

    @Test
    @DisplayName("Login returns 400 for invalid credentials")
    void loginInvalidCredentials() {
        String username = "user-" + UUID.randomUUID();
        String password = "testpassword";
        cleanupUsernames.add(username);

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .apiKey(UUID.randomUUID().toString())
                .roles("USER")
                .build();
        userRepository.save(user);

        AuthRequest request = new AuthRequest();
        request.setUsername(username);
        request.setPassword("wrong-password");

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Validation errors return 400")
    void validationErrors() {
        AuthRequest request = new AuthRequest();
        request.setUsername("");
        request.setPassword("");

        webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }
}
