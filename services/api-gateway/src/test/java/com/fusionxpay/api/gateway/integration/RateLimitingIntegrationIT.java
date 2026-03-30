package com.fusionxpay.api.gateway.integration;

import com.fusionxpay.common.security.JwtClaims;
import com.fusionxpay.common.security.JwtUtils;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.gateway.server.webflux.discovery.locator.enabled=false",
        "jwt.secret=fusionxpay-admin-jwt-secret-key-must-be-at-least-256-bits-long",
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
class RateLimitingIntegrationIT {

    private static final String TEST_SECRET = "fusionxpay-admin-jwt-secret-key-must-be-at-least-256-bits-long";

    private static WireMockServer backendServer;

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private final JwtUtils jwtUtils = new JwtUtils(TEST_SECRET);

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

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
        registry.add("ADMIN_SERVICE_URI", () -> backendUrl);
        registry.add("ORDER_SERVICE_URI", () -> backendUrl);
        registry.add("PAYMENT_SERVICE_URI", () -> backendUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

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
        ensureBackendServer().resetRequests();
    }

    @Test
    @DisplayName("Admin login endpoint is rate limited by source IP")
    void adminLoginIsRateLimitedByIp() {
        int burstCapacity = 2;
        int margin = 2;

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
    @DisplayName("Payment endpoint is rate limited by merchant identity")
    void paymentEndpointIsRateLimitedByMerchantId() {
        String token = tokenForMerchant(101L);
        int burstCapacity = 3;
        int margin = 2;

        for (int i = 0; i < burstCapacity + margin; i++) {
            webTestClient.post()
                    .uri("/api/v1/payment/request")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("orderId", "order-" + i, "amount", 100))
                    .exchange();
        }

        webTestClient.post()
                .uri("/api/v1/payment/request")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("orderId", "order-throttled", "amount", 100))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "60");
    }

    @Test
    @DisplayName("Order endpoint is rate limited by merchant identity with its own threshold")
    void orderEndpointUsesIndependentThreshold() {
        String token = tokenForMerchant(202L);
        int burstCapacity = 4;
        int margin = 2;

        for (int i = 0; i < burstCapacity + margin; i++) {
            webTestClient.get()
                    .uri("/api/v1/orders")
                    .header("Authorization", "Bearer " + token)
                    .exchange();
        }

        webTestClient.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "60");
    }

    @Test
    @DisplayName("Different merchants get isolated payment rate limit buckets")
    void differentMerchantsHaveIndependentBuckets() {
        String merchantOneToken = tokenForMerchant(301L);
        String merchantTwoToken = tokenForMerchant(302L);
        int burstCapacity = 3;
        int margin = 2;

        for (int i = 0; i < burstCapacity + margin; i++) {
            webTestClient.post()
                    .uri("/api/v1/payment/request")
                    .header("Authorization", "Bearer " + merchantOneToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("orderId", "merchant-one-" + i, "amount", 100))
                    .exchange();
        }

        webTestClient.post()
                .uri("/api/v1/payment/request")
                .header("Authorization", "Bearer " + merchantOneToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("orderId", "merchant-one-throttled", "amount", 100))
                .exchange()
                .expectStatus().isEqualTo(429);

        webTestClient.post()
                .uri("/api/v1/payment/request")
                .header("Authorization", "Bearer " + merchantTwoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("orderId", "merchant-two-ok", "amount", 100))
                .exchange()
                .expectStatus().isOk();

        ensureBackendServer().verify(postRequestedFor(urlEqualTo("/api/v1/payment/request"))
                .withHeader("X-Merchant-Id", equalTo("302")));
    }

    private String tokenForMerchant(Long merchantId) {
        return jwtUtils.generateToken(
                new JwtClaims(merchantId, "merchant-" + merchantId + "@example.com", "MERCHANT"),
                60_000
        );
    }
}
