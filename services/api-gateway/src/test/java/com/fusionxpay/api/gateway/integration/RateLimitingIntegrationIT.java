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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @TestConfiguration
    static class TestOverrideRoutesConfig {

        @Bean
        RouteLocator testOverrideRoutes(
                RouteLocatorBuilder builder,
                @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver,
                @Qualifier("apiKeyKeyResolver") KeyResolver apiKeyKeyResolver,
                @Value("${app.rate-limit.login.replenish-rate}") int loginReplenishRate,
                @Value("${app.rate-limit.login.burst-capacity}") int loginBurstCapacity,
                @Value("${app.rate-limit.login.requested-tokens}") int loginRequestedTokens,
                @Value("${app.rate-limit.payment.replenish-rate}") int paymentReplenishRate,
                @Value("${app.rate-limit.payment.burst-capacity}") int paymentBurstCapacity,
                @Value("${app.rate-limit.payment.requested-tokens}") int paymentRequestedTokens,
                @Value("${app.rate-limit.orders.replenish-rate}") int ordersReplenishRate,
                @Value("${app.rate-limit.orders.burst-capacity}") int ordersBurstCapacity,
                @Value("${app.rate-limit.orders.requested-tokens}") int ordersRequestedTokens
        ) {
            String backendUrl = "http://localhost:" + ensureBackendServer().port();

            // Provide deterministic local backends for rate-limiting tests.
            // This avoids flakiness from service discovery / downstream availability.
            return builder.routes()
                    .route("test-admin-login-v1", r -> r.order(-200)
                            .path("/api/v1/admin/auth/login")
                            .filters(f -> f.requestRateLimiter(c -> {
                                c.setKeyResolver(ipKeyResolver);
                                c.setRateLimiter(new RedisRateLimiter(loginReplenishRate, loginBurstCapacity, loginRequestedTokens));
                            }))
                            .uri(backendUrl))
                    .route("test-payment-v1", r -> r.order(-200)
                            .path("/api/v1/payment/request")
                            .filters(f -> f.requestRateLimiter(c -> {
                                c.setKeyResolver(apiKeyKeyResolver);
                                c.setRateLimiter(new RedisRateLimiter(paymentReplenishRate, paymentBurstCapacity, paymentRequestedTokens));
                            }))
                            .uri(backendUrl))
                    .route("test-orders-v1", r -> r.order(-200)
                            .path("/api/v1/orders", "/api/v1/orders/**")
                            .filters(f -> f.requestRateLimiter(c -> {
                                c.setKeyResolver(apiKeyKeyResolver);
                                c.setRateLimiter(new RedisRateLimiter(ordersReplenishRate, ordersBurstCapacity, ordersRequestedTokens));
                            }))
                            .uri(backendUrl))
                    .build();
        }
    }

    @Test
    @DisplayName("Admin login endpoint is rate limited by source IP")
    void adminLoginIsRateLimitedByIp() {
        // ipKeyResolver now uses remoteAddress directly (X-Forwarded-For is not trusted)
        // All requests from this test share the same loopback IP, so burst-capacity=2 applies

        for (int i = 0; i < 2; i++) {
            assertNotRateLimited(
                    webTestClient.post()
                            .uri("/api/v1/admin/auth/login")
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
