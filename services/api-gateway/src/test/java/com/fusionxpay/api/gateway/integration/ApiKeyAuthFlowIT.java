package com.fusionxpay.api.gateway.integration;

import com.fusionxpay.api.gateway.dto.AuthRequest;
import com.fusionxpay.api.gateway.model.MerchantAccount;
import com.fusionxpay.api.gateway.model.MerchantApiKeyRecord;
import com.fusionxpay.api.gateway.model.MerchantStatus;
import com.fusionxpay.api.gateway.repository.MerchantAccountRepository;
import com.fusionxpay.api.gateway.repository.MerchantApiKeyRecordRepository;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for API Key authentication flow in api-gateway.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false"
})
public class ApiKeyAuthFlowIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MerchantAccountRepository merchantAccountRepository;

    @Autowired
    private MerchantApiKeyRecordRepository merchantApiKeyRecordRepository;

    private WebTestClient webTestClient;

    private static final String VALID_API_KEY = "fxp_integration_valid_key_123456";
    private static final String INVALID_API_KEY = "invalid-api-key-12345";

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        merchantApiKeyRecordRepository.deleteAll();
        merchantAccountRepository.deleteAll();

        MerchantAccount merchant = MerchantAccount.builder()
                .email("merchant-it@example.com")
                .status(MerchantStatus.ACTIVE)
                .build();
        merchant = merchantAccountRepository.save(merchant);

        MerchantApiKeyRecord record = MerchantApiKeyRecord.builder()
                .merchantId(merchant.getId())
                .keyHash(sha256(VALID_API_KEY))
                .active(true)
                .build();
        merchantApiKeyRecordRepository.save(record);
    }

    @Test
    @DisplayName("Valid API Key passes authentication")
    void testValidApiKey_PassesAuthentication() {
        webTestClient.get()
                .uri("/order-service/orders")
                .header("X-API-Key", VALID_API_KEY)
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
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
    @DisplayName("Deprecated auth endpoints return 410")
    void testAuthEndpoints_Deprecated() {
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
    @DisplayName("Swagger endpoints bypass API key validation")
    void testSwaggerEndpoints_BypassApiKeyValidation() {
        webTestClient.get()
                .uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("API docs endpoints bypass API key validation")
    void testApiDocsEndpoints_BypassApiKeyValidation() {
        webTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("Multiple valid API keys work independently")
    void testMultipleValidApiKeys_WorkIndependently() {
        MerchantAccount merchant = MerchantAccount.builder()
                .email("merchant-two@example.com")
                .status(MerchantStatus.ACTIVE)
                .build();
        merchant = merchantAccountRepository.save(merchant);

        String secondApiKey = "fxp_second_key_" + UUID.randomUUID();
        MerchantApiKeyRecord secondRecord = MerchantApiKeyRecord.builder()
                .merchantId(merchant.getId())
                .keyHash(sha256(secondApiKey))
                .active(true)
                .build();
        merchantApiKeyRecordRepository.save(secondRecord);

        webTestClient.get()
                .uri("/order-service/orders")
                .header("X-API-Key", VALID_API_KEY)
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));

        webTestClient.get()
                .uri("/order-service/orders")
                .header("X-API-Key", secondApiKey)
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));

        webTestClient.get()
                .uri("/order-service/orders")
                .header("X-API-Key", INVALID_API_KEY)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
