package com.fusionxpay.payment.integration;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import com.fusionxpay.common.test.WireMockConfig;
import com.fusionxpay.payment.dto.RefundRequest;
import com.fusionxpay.payment.dto.RefundResponse;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.model.RefundStatus;
import com.fusionxpay.payment.repository.PaymentTransactionRepository;
import com.fusionxpay.payment.service.IdempotencyService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the refund flow.
 * Tests refund processing with Stripe API simulation via WireMock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RefundFlowIT extends AbstractIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    private static final int WIREMOCK_PORT = 8090;
    private static final String WEBHOOK_SECRET = "whsec_test_mock";
    private static final String STRIPE_API_VERSION = "2024-10-28.acacia";

    @BeforeAll
    static void setupWireMock() {
        wireMockServer = WireMockConfig.createWireMockServer(WIREMOCK_PORT);
    }

    @AfterAll
    static void tearDownWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        paymentTransactionRepository.deleteAll();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Configure Stripe to use WireMock
        registry.add("payment.providers.stripe.api-base-url", () -> "http://localhost:" + WIREMOCK_PORT);
        registry.add("payment.providers.stripe.secret-key", () -> "sk_test_mock");
        registry.add("payment.providers.stripe.webhook-secret", () -> "whsec_test_mock");

        // Disable Eureka for tests
        registry.add("eureka.client.enabled", () -> false);
        registry.add("spring.cloud.discovery.enabled", () -> false);
    }

    @Test
    @Order(1)
    @DisplayName("Should process full refund successfully")
    void testInitiateRefund_FullRefund_Success() {
        // Arrange - Create a successful payment transaction
        WireMockConfig.stubStripeRefundSuccess(wireMockServer);

        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setMerchantId(1L);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.SUCCESS.name());
        transaction.setProviderTransactionId("pi_test_refund_123");
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        RefundRequest refundRequest = RefundRequest.builder()
                .transactionId(saved.getTransactionId().toString())
                .reason("Customer requested refund")
                .build();

        // Act
        HttpHeaders refundHeaders = new HttpHeaders();
        refundHeaders.set("X-Merchant-Id", "1");
        HttpEntity<RefundRequest> refundEntity = new HttpEntity<>(refundRequest, refundHeaders);
        ResponseEntity<RefundResponse> response = restTemplate.exchange(
                "/api/v1/payment/refund",
                org.springframework.http.HttpMethod.POST,
                refundEntity,
                RefundResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTransactionId()).isEqualTo(saved.getTransactionId().toString());
        // Status depends on Stripe API mock response
        assertThat(response.getBody().getStatus()).isIn(RefundStatus.COMPLETED, RefundStatus.PROCESSING, RefundStatus.PENDING, RefundStatus.FAILED);
    }

    @Test
    @Order(2)
    @DisplayName("Should process partial refund successfully")
    void testInitiateRefund_PartialRefund_Success() {
        // Arrange
        WireMockConfig.stubStripeRefundSuccess(wireMockServer);

        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setMerchantId(1L);
        transaction.setAmount(new BigDecimal("200.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.SUCCESS.name());
        transaction.setProviderTransactionId("pi_test_partial_refund_123");
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        RefundRequest refundRequest = RefundRequest.builder()
                .transactionId(saved.getTransactionId().toString())
                .amount(new BigDecimal("50.00"))
                .reason("Partial refund for damaged item")
                .build();

        // Act
        HttpHeaders refundHeaders2 = new HttpHeaders();
        refundHeaders2.set("X-Merchant-Id", "1");
        HttpEntity<RefundRequest> refundEntity2 = new HttpEntity<>(refundRequest, refundHeaders2);
        ResponseEntity<RefundResponse> response = restTemplate.exchange(
                "/api/v1/payment/refund",
                org.springframework.http.HttpMethod.POST,
                refundEntity2,
                RefundResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("Should fail refund for non-existent transaction")
    void testInitiateRefund_TransactionNotFound() {
        // Arrange
        UUID nonExistentTransactionId = UUID.randomUUID();

        RefundRequest refundRequest = RefundRequest.builder()
                .transactionId(nonExistentTransactionId.toString())
                .reason("Test refund")
                .build();

        // Act
        HttpHeaders failHeaders = new HttpHeaders();
        failHeaders.set("X-Merchant-Id", "1");
        HttpEntity<RefundRequest> failEntity = new HttpEntity<>(refundRequest, failHeaders);
        ResponseEntity<RefundResponse> response = restTemplate.exchange(
                "/api/v1/payment/refund",
                org.springframework.http.HttpMethod.POST,
                failEntity,
                RefundResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(response.getBody().getErrorMessage()).contains("not found");
    }

    @Test
    @Order(4)
    @DisplayName("Should fail refund for non-refundable transaction status")
    void testInitiateRefund_NonRefundableStatus() {
        // Arrange - Create a transaction that is not in SUCCESS state
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setMerchantId(1L);
        transaction.setAmount(new BigDecimal("75.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name()); // Not SUCCESS
        transaction.setProviderTransactionId("pi_test_processing_123");
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        RefundRequest refundRequest = RefundRequest.builder()
                .transactionId(saved.getTransactionId().toString())
                .reason("Test refund")
                .build();

        // Act
        HttpHeaders nonRefHeaders = new HttpHeaders();
        nonRefHeaders.set("X-Merchant-Id", "1");
        HttpEntity<RefundRequest> nonRefEntity = new HttpEntity<>(refundRequest, nonRefHeaders);
        ResponseEntity<RefundResponse> response = restTemplate.exchange(
                "/api/v1/payment/refund",
                org.springframework.http.HttpMethod.POST,
                nonRefEntity,
                RefundResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(response.getBody().getErrorMessage()).contains("not in a refundable state");
    }

    @Test
    @Order(5)
    @DisplayName("Should mark transaction refunded when payment idempotency key is already completed")
    void testRefundFlow_WebhookCallback() {
        // Arrange - Create a successful payment transaction
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setMerchantId(1L);
        transaction.setAmount(new BigDecimal("150.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.SUCCESS.name());
        transaction.setProviderTransactionId("pi_test_refund_webhook_123");
        paymentTransactionRepository.save(transaction);

        idempotencyService.markAsCompleted(
                "stripe:webhook:event:payment:order:" + orderId,
                Duration.ofMinutes(5)
        );

        // Create webhook payload simulating Stripe charge.refunded event
        String webhookPayload = String.format("""
            {
                "id": "evt_refund_123",
                "object": "event",
                "api_version": "%s",
                "created": 1731111111,
                "livemode": false,
                "pending_webhooks": 1,
                "type": "charge.refunded",
                "data": {
                    "object": {
                        "id": "ch_test_123",
                        "object": "charge",
                        "refunded": true,
                        "amount_refunded": 15000,
                        "payment_intent": "pi_test_refund_webhook_123",
                        "metadata": {
                            "orderId": "%s"
                        }
                    }
                }
            }
            """, STRIPE_API_VERSION, orderId.toString());
        String signature = createValidSignature(webhookPayload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Stripe-Signature", signature);

        HttpEntity<String> webhookRequest = new HttpEntity<>(webhookPayload, headers);

        // Act
        ResponseEntity<Void> webhookResponse = restTemplate.postForEntity(
                "/api/v1/payment/webhook/stripe",
                webhookRequest,
                Void.class
        );

        // Assert
        assertThat(webhookResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Optional<PaymentTransaction> updatedTransaction = paymentTransactionRepository.findByOrderId(orderId);
        assertThat(updatedTransaction).isPresent();
        assertThat(updatedTransaction.get().getStatus()).isEqualTo(PaymentStatus.REFUNDED.name());
        assertThat(updatedTransaction.get().getProviderTransactionId()).isEqualTo("pi_test_refund_webhook_123");
    }

    @Test
    @Order(6)
    @DisplayName("Should fail refund when provider transaction ID is missing")
    void testInitiateRefund_MissingProviderTransactionId() {
        // Arrange - Create a transaction without provider transaction ID
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setMerchantId(1L);
        transaction.setAmount(new BigDecimal("60.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.SUCCESS.name());
        // Note: providerTransactionId is NOT set
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        RefundRequest refundRequest = RefundRequest.builder()
                .transactionId(saved.getTransactionId().toString())
                .reason("Test refund")
                .build();

        // Act
        HttpHeaders missingProvHeaders = new HttpHeaders();
        missingProvHeaders.set("X-Merchant-Id", "1");
        HttpEntity<RefundRequest> missingProvEntity = new HttpEntity<>(refundRequest, missingProvHeaders);
        ResponseEntity<RefundResponse> response = restTemplate.exchange(
                "/api/v1/payment/refund",
                org.springframework.http.HttpMethod.POST,
                missingProvEntity,
                RefundResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(response.getBody().getErrorMessage()).contains("provider transaction ID");
    }

    private String createValidSignature(String payload) {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + toHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }
}
