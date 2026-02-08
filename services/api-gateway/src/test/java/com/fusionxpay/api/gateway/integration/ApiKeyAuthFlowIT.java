package com.fusionxpay.api.gateway.integration;

import com.fusionxpay.api.gateway.dto.AuthRequest;
import com.fusionxpay.api.gateway.dto.AuthResponse;
import com.fusionxpay.api.gateway.model.User;
import com.fusionxpay.api.gateway.repository.UserRepository;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for API Key authentication flow in api-gateway
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApiKeyAuthFlowIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private WebTestClient webTestClient;

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "testpass123";
    private static final String VALID_API_KEY = "valid-api-key-" + UUID.randomUUID();
    private static final String INVALID_API_KEY = "invalid-api-key-12345";

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        userRepository.deleteAll();

        // Create test user with valid API key
        User testUser = User.builder()
                .username(TEST_USERNAME)
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .apiKey(VALID_API_KEY)
                .roles("USER")
                .build();
        userRepository.save(testUser);
    }

    @Test
    @DisplayName("Valid API Key passes authentication")
    void testValidApiKey_PassesAuthentication() {
        // Request to a protected endpoint with valid API key
        // Using order-service route as example
        webTestClient.get()
                .uri("/order-service/orders")
                .header("X-API-Key", VALID_API_KEY)
                .exchange()
                .expectStatus().isNotFound();  // 404 because backend service is not running, but auth passed

        // The fact that we get 404 (or 503) instead of 401 means authentication passed
    }

    @Test
    @DisplayName("Invalid API Key is rejected with 401")
    void testInvalidApiKey_Returns401() {
        webTestClient.get()
                .uri("/order-service/orders")
                .header("X-API-Key", INVALID_API_KEY)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Missing X-API-Key header is rejected with 401")
    void testMissingApiKeyHeader_Returns401() {
        webTestClient.get()
                .uri("/order-service/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Empty API Key is rejected with 401")
    void testEmptyApiKey_Returns401() {
        webTestClient.get()
                .uri("/order-service/orders")
                .header("X-API-Key", "")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Auth endpoints bypass API key validation")
    void testAuthEndpoints_BypassApiKeyValidation() {
        // Registration endpoint should be accessible without API key
        AuthRequest registerRequest = new AuthRequest();
        registerRequest.setUsername("newuser");
        registerRequest.setPassword("newpassword");

        webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .value(response -> {
                    assertThat(response.getUsername()).isEqualTo("newuser");
                    assertThat(response.getApiKey()).isNotBlank();
                });
    }

    @Test
    @DisplayName("Login endpoint returns API key for valid credentials")
    void testLogin_ReturnsApiKey() {
        AuthRequest loginRequest = new AuthRequest();
        loginRequest.setUsername(TEST_USERNAME);
        loginRequest.setPassword(TEST_PASSWORD);

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .value(response -> {
                    assertThat(response.getUsername()).isEqualTo(TEST_USERNAME);
                    assertThat(response.getApiKey()).isEqualTo(VALID_API_KEY);
                });
    }

    @Test
    @DisplayName("Login with invalid credentials returns error")
    void testLogin_WithInvalidCredentials_ReturnsError() {
        AuthRequest loginRequest = new AuthRequest();
        loginRequest.setUsername(TEST_USERNAME);
        loginRequest.setPassword("wrongpassword");

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus().is5xxServerError();  // Service throws exception
    }

    @Test
    @DisplayName("Swagger UI endpoints bypass API key validation")
    void testSwaggerEndpoints_BypassApiKeyValidation() {
        // Swagger UI should be accessible without API key
        webTestClient.get()
                .uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isNotFound();  // May return 404 if swagger not configured, but not 401
    }

    @Test
    @DisplayName("API docs endpoints bypass API key validation")
    void testApiDocsEndpoints_BypassApiKeyValidation() {
        webTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isNotFound();  // May return 404 if not configured, but not 401
    }

    @Test
    @DisplayName("Newly registered user can authenticate with their API key")
    void testRegisteredUser_CanAuthenticateWithApiKey() {
        // Register a new user
        AuthRequest registerRequest = new AuthRequest();
        registerRequest.setUsername("brandnewuser");
        registerRequest.setPassword("brandnewpass");

        AuthResponse registerResponse = webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(registerResponse).isNotNull();
        String newApiKey = registerResponse.getApiKey();
        assertThat(newApiKey).isNotBlank();

        // Use the new API key to access protected endpoint
        webTestClient.get()
                .uri("/order-service/orders")
                .header("X-API-Key", newApiKey)
                .exchange()
                .expectStatus().isNotFound();  // Auth passed (not 401), service not available (404/503)
    }

    @Test
    @DisplayName("Multiple valid API keys work independently")
    void testMultipleValidApiKeys_WorkIndependently() {
        // Create second user with different API key
        String secondApiKey = "second-api-key-" + UUID.randomUUID();
        User secondUser = User.builder()
                .username("seconduser")
                .password(passwordEncoder.encode("secondpass"))
                .apiKey(secondApiKey)
                .roles("USER")
                .build();
        userRepository.save(secondUser);

        // First API key should work
        webTestClient.get()
                .uri("/order-service/orders")
                .header("X-API-Key", VALID_API_KEY)
                .exchange()
                .expectStatus().isNotFound();

        // Second API key should also work
        webTestClient.get()
                .uri("/order-service/orders")
                .header("X-API-Key", secondApiKey)
                .exchange()
                .expectStatus().isNotFound();

        // Invalid key should still fail
        webTestClient.get()
                .uri("/order-service/orders")
                .header("X-API-Key", INVALID_API_KEY)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
