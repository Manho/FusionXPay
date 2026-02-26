package com.fusionxpay.api.gateway.controller;

import com.fusionxpay.api.gateway.dto.AuthRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Register endpoint returns 410 after deprecation")
    void registerDeprecated() {
        AuthRequest request = new AuthRequest();
        request.setUsername("user@example.com");
        request.setPassword("testpassword");

        webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DEPRECATED_ENDPOINT");
    }

    @Test
    @DisplayName("Login endpoint returns 410 after deprecation")
    void loginDeprecated() {
        AuthRequest request = new AuthRequest();
        request.setUsername("user@example.com");
        request.setPassword("testpassword");

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DEPRECATED_ENDPOINT");
    }

    @Test
    @DisplayName("Validation errors still return 400")
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
