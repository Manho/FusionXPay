package com.fusionxpay.api.gateway.integration;

import com.fusionxpay.api.gateway.model.User;
import com.fusionxpay.api.gateway.repository.UserRepository;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "app.rate-limit.login.replenish-rate=1",
        "app.rate-limit.login.burst-capacity=2",
        "app.rate-limit.login.requested-tokens=1",
        "app.rate-limit.payment.replenish-rate=1",
        "app.rate-limit.payment.burst-capacity=3",
        "app.rate-limit.payment.requested-tokens=1",
        "app.rate-limit.orders.replenish-rate=1",
        "app.rate-limit.orders.burst-capacity=4",
        "app.rate-limit.orders.requested-tokens=1"
})
class RateLimitingIntegrationIT extends AbstractIntegrationTest {

    private static WireMockServer backendServer;

    private static synchronized WireMockServer ensureBackendServer() {
        if (backendServer == null) {
            backendServer = new WireMockServer(options().dynamicPort());
            backendServer.start();
            backendServer.stubFor(any(anyUrl())
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{}")));
        }
        return backendServer;
    }

    @DynamicPropertySource
    static void overrideGatewayBackends(DynamicPropertyRegistry registry) {
        String backendUrl = "http://localhost:" + ensureBackendServer().port();

        // Point gateway routes to a deterministic local backend so rate limiting tests
        // don't depend on service discovery / downstream availability.
        // Uses URI template variables defined in application.yml to avoid Spring
        // property binding conflicts with YAML-defined route lists.
        registry.add("ADMIN_SERVICE_URI", () -> backendUrl);
        registry.add("ORDER_SERVICE_URI", () -> backendUrl);
        registry.add("PAYMENT_SERVICE_URI", () -> backendUrl);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private WebTestClient webTestClient;

    @AfterAll
    static void stopBackendServer() {
        if (backendServer != null) {
            backendServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Admin login endpoint is rate limited by source IP")
    void adminLoginIsRateLimitedByIp() {
        // burst-capacity=2, replenish-rate=1.  The Redis token-bucket uses epoch-second
        // granularity, so crossing a second boundary may replenish 1 extra token.
        // We therefore exhaust the bucket with a generous margin and then assert 429.
        int burstCapacity = 2;
        int margin = 2; // allow for token replenishment across second boundaries

        for (int i = 0; i < burstCapacity + margin; i++) {
            webTestClient.post()
                    .uri("/api/v1/admin/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("username", "admin", "password", "admin123"))
                    .exchange();
        }

        webTestClient.post()
                .uri("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin", "password", "admin123"))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "60");
    }

    @Test
    @DisplayName("Payment endpoint is rate limited by API key")
    void paymentEndpointIsRateLimitedByApiKey() {
        String apiKey = createTestUserAndReturnApiKey();
        int burstCapacity = 3;
        int margin = 2;

        for (int i = 0; i < burstCapacity + margin; i++) {
            webTestClient.post()
                    .uri("/api/v1/payment/request")
                    .header("X-API-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("orderId", "order-" + i, "amount", 100))
                    .exchange();
        }

        webTestClient.post()
                .uri("/api/v1/payment/request")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("orderId", "order-throttled", "amount", 100))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "60");
    }

    @Test
    @DisplayName("Order endpoint is rate limited by API key with its own threshold")
    void orderEndpointUsesIndependentThreshold() {
        String apiKey = createTestUserAndReturnApiKey();
        int burstCapacity = 4;
        int margin = 2;

        for (int i = 0; i < burstCapacity + margin; i++) {
            webTestClient.get()
                    .uri("/api/v1/orders")
                    .header("X-API-Key", apiKey)
                    .exchange();
        }

        webTestClient.get()
                .uri("/api/v1/orders")
                .header("X-API-Key", apiKey)
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "60");
    }

    private String createTestUserAndReturnApiKey() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "rate-limit-user-" + suffix;
        String apiKey = "rate-limit-key-" + UUID.randomUUID();

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode("test-password-123"))
                .apiKey(apiKey)
                .roles("USER")
                .build();
        userRepository.save(user);
        return apiKey;
    }
}
