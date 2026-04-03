package com.fusionxpay.api.gateway.integration;

import com.fusionxpay.api.gateway.audit.PlatformAuditPublisher;
import com.fusionxpay.common.audit.PlatformAuditEvent;
import com.fusionxpay.common.audit.PlatformAuditHeaders;
import com.fusionxpay.common.security.JwtClaims;
import com.fusionxpay.common.security.JwtUtils;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.gateway.server.webflux.discovery.locator.enabled=false",
        "jwt.secret=fusionxpay-admin-jwt-secret-key-must-be-at-least-256-bits-long",
        "fusionx.platform.audit.enabled=false"
})
class PlatformAuditFlowIT {

    private static final String TEST_SECRET = "fusionxpay-admin-jwt-secret-key-must-be-at-least-256-bits-long";
    private static WireMockServer backendServer;

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private final JwtUtils jwtUtils = new JwtUtils(TEST_SECRET);

    @LocalServerPort
    private int port;

    @MockBean
    private PlatformAuditPublisher platformAuditPublisher;

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
    @DisplayName("Gateway generates, forwards, and returns correlation IDs while publishing platform audit events")
    void gatewayGeneratesAndEchoesCorrelationId() {
        String token = jwtUtils.generateToken(new JwtClaims(101L, "merchant-it@example.com", "MERCHANT"), 60_000);

        EntityExchangeResult<byte[]> result = webTestClient.get()
                .uri("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(PlatformAuditHeaders.AUDIT_CORRELATION_ID)
                .expectBody()
                .returnResult();

        String correlationId = result.getResponseHeaders().getFirst(PlatformAuditHeaders.AUDIT_CORRELATION_ID);
        assertThat(correlationId).isNotBlank();

        ensureBackendServer().verify(getRequestedFor(urlEqualTo("/api/v1/orders"))
                .withHeader("X-Merchant-Id", equalTo("101"))
                .withHeader(PlatformAuditHeaders.AUDIT_CORRELATION_ID, equalTo(correlationId)));

        ArgumentCaptor<PlatformAuditEvent> captor = ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(platformAuditPublisher).publish(captor.capture());
        assertThat(captor.getValue().getCorrelationId()).isEqualTo(correlationId);
        assertThat(captor.getValue().getMerchantId()).isEqualTo(101L);
        assertThat(captor.getValue().getSource()).isEqualTo("UNKNOWN");
        assertThat(captor.getValue().getActionName()).isEqualTo("GET /api/v1/orders");
        assertThat(captor.getValue().getPath()).isEqualTo("/api/v1/orders");
        assertThat(captor.getValue().getStatusCode()).isEqualTo(200);
    }
}
