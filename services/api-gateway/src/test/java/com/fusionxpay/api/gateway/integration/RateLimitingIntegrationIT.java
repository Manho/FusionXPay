package com.fusionxpay.api.gateway.integration;

import com.fusionxpay.api.gateway.model.User;
import com.fusionxpay.api.gateway.repository.UserRepository;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private WebTestClient webTestClient;

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
        String clientIp = "203.0.113." + (10 + (int) (Math.random() * 100));

        for (int i = 0; i < 2; i++) {
            assertNotRateLimited(
                    webTestClient.post()
                            .uri("/api/v1/admin/auth/login")
                            .header("X-Forwarded-For", clientIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("username", "admin", "password", "admin123"))
                            .exchange()
                            .returnResult(String.class)
                            .getStatus()
                            .value()
            );
        }

        webTestClient.post()
                .uri("/api/v1/admin/auth/login")
                .header("X-Forwarded-For", clientIp)
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

        for (int i = 0; i < 3; i++) {
            assertNotRateLimited(
                    webTestClient.post()
                            .uri("/api/v1/payment/request")
                            .header("X-API-Key", apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("orderId", "order-" + i, "amount", 100))
                            .exchange()
                            .returnResult(String.class)
                            .getStatus()
                            .value()
            );
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

        for (int i = 0; i < 4; i++) {
            assertNotRateLimited(
                    webTestClient.get()
                            .uri("/api/v1/orders")
                            .header("X-API-Key", apiKey)
                            .exchange()
                            .returnResult(String.class)
                            .getStatus()
                            .value()
            );
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

    private void assertNotRateLimited(int statusCode) {
        assertThat(statusCode).isNotEqualTo(429);
    }
}
