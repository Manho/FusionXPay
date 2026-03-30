package com.fusionxpay.api.gateway.integration;

import com.fusionxpay.api.gateway.dto.AuthRequest;
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

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.gateway.server.webflux.discovery.locator.enabled=false",
        "jwt.secret=fusionxpay-admin-jwt-secret-key-must-be-at-least-256-bits-long"
})
class JwtAuthFlowIT {

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
    @DisplayName("Valid JWT passes authentication and forwards merchant headers")
    void validJwtPassesAuthentication() {
        String token = jwtUtils.generateToken(new JwtClaims(101L, "merchant-it@example.com", "MERCHANT"), 60_000);

        webTestClient.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        ensureBackendServer().verify(getRequestedFor(urlEqualTo("/api/v1/orders"))
                .withHeader("X-Merchant-Id", equalTo("101"))
                .withHeader("X-Merchant-Role", equalTo("MERCHANT")));
    }

    @Test
    @DisplayName("Invalid JWT is rejected with 401")
    void invalidJwtReturns401() {
        webTestClient.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer invalid.jwt.token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Missing bearer token is rejected with 401")
    void missingJwtHeaderReturns401() {
        webTestClient.get()
                .uri("/api/v1/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Deprecated auth endpoints return 410")
    void authEndpointsDeprecated() {
        AuthRequest request = new AuthRequest();
        request.setUsername("newuser");
        request.setPassword("newpassword");

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
    @DisplayName("Admin and docs endpoints bypass JWT validation")
    void bypassEndpointsRemainAccessibleWithoutJwt() {
        webTestClient.get()
                .uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));

        webTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("Payment route forwards merchant headers with valid JWT")
    void paymentRouteForwardsHeaders() {
        String token = jwtUtils.generateToken(new JwtClaims(202L, "merchant-two@example.com", "MERCHANT"), 60_000);

        webTestClient.post()
                .uri("/api/v1/payment/request")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "orderId": "00000000-0000-0000-0000-000000000001",
                          "amount": 100.00,
                          "currency": "USD",
                          "paymentChannel": "STRIPE"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();

        ensureBackendServer().verify(postRequestedFor(urlEqualTo("/api/v1/payment/request"))
                .withHeader("X-Merchant-Id", equalTo("202"))
                .withHeader("X-Merchant-Role", equalTo("MERCHANT")));
    }
}
