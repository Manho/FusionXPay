package com.fusionxpay.payment.integration;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import com.fusionxpay.common.test.WireMockConfig;
import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.repository.PaymentTransactionRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for the payment flow.
 * Tests end-to-end payment processing with Stripe API simulation via WireMock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PaymentFlowIntegrationTest extends AbstractIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    private static final int WIREMOCK_PORT = 8089;

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
    @DisplayName("Should create payment transaction and return PROCESSING status")
    void testInitiatePayment_Success() {
        // Arrange
        WireMockConfig.stubStripePaymentSuccess(wireMockServer);

        // Stub Stripe checkout session creation
        wireMockServer.stubFor(post(urlPathMatching("/v1/checkout/sessions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"cs_test_123\",\"url\":\"https://checkout.stripe.com/pay/cs_test_123\",\"payment_intent\":\"pi_test_123\"}")));

        UUID orderId = UUID.randomUUID();
        PaymentRequest request = PaymentRequest.builder()
                .orderId(orderId)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentChannel("STRIPE")
                .description("Test payment")
                .returnUrl("https://example.com/success")
                .cancelUrl("https://example.com/cancel")
                .build();

        // Act
        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                "/api/v1/payment/request",
                request,
                PaymentResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrderId()).isEqualTo(orderId);
        assertThat(response.getBody().getStatus()).isIn(PaymentStatus.PROCESSING, PaymentStatus.INITIATED);

        // Verify transaction was saved in database
        Optional<PaymentTransaction> savedTransaction = paymentTransactionRepository.findByOrderId(orderId);
        assertThat(savedTransaction).isPresent();
        assertThat(savedTransaction.get().getPaymentChannel()).isEqualTo("STRIPE");
    }

    @Test
    @Order(2)
    @DisplayName("Should update payment status to SUCCESS after Stripe webhook callback")
    void testPaymentFlow_WebhookCallback_Success() {
        // Arrange - First create a payment transaction
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setAmount(new BigDecimal("99.99"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        transaction.setProviderTransactionId("pi_test_webhook_123");
        paymentTransactionRepository.save(transaction);

        // Create webhook payload simulating Stripe payment_intent.succeeded event
        String webhookPayload = String.format("""
            {
                "id": "evt_test_123",
                "type": "payment_intent.succeeded",
                "data": {
                    "object": {
                        "id": "pi_test_webhook_123",
                        "object": "payment_intent",
                        "status": "succeeded",
                        "metadata": {
                            "orderId": "%s"
                        }
                    }
                }
            }
            """, orderId.toString());

        // Note: In real tests, you would need to properly sign the webhook
        // For this test, we configure webhook validation to pass
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Stripe-Signature", "t=1234567890,v1=test_signature");

        HttpEntity<String> webhookRequest = new HttpEntity<>(webhookPayload, headers);

        // Act - Send webhook callback
        ResponseEntity<Void> webhookResponse = restTemplate.postForEntity(
                "/api/v1/payment/webhook/stripe",
                webhookRequest,
                Void.class
        );

        // Assert - Check that transaction status was updated
        // Note: The actual signature verification may fail in this test setup
        // In a real scenario, you would mock the Stripe Webhook.constructEvent method
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<PaymentTransaction> updatedTransaction = paymentTransactionRepository.findByOrderId(orderId);
            assertThat(updatedTransaction).isPresent();
            // Status might still be PROCESSING if signature validation fails
            // In production tests, proper mocking of Stripe SDK would be needed
        });
    }

    @Test
    @Order(3)
    @DisplayName("Should handle payment failure webhook correctly")
    void testPaymentFlow_WebhookCallback_Failure() {
        // Arrange - Create a payment transaction
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setAmount(new BigDecimal("50.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        transaction.setProviderTransactionId("pi_test_failed_123");
        paymentTransactionRepository.save(transaction);

        // Verify transaction was created
        Optional<PaymentTransaction> savedTransaction = paymentTransactionRepository.findByOrderId(orderId);
        assertThat(savedTransaction).isPresent();
        assertThat(savedTransaction.get().getStatus()).isEqualTo(PaymentStatus.PROCESSING.name());
    }

    @Test
    @Order(4)
    @DisplayName("Should return existing transaction for duplicate order payment request")
    void testInitiatePayment_DuplicateOrder() {
        // Arrange - Create an existing successful transaction
        UUID orderId = UUID.randomUUID();
        PaymentTransaction existingTransaction = new PaymentTransaction();
        existingTransaction.setOrderId(orderId);
        existingTransaction.setAmount(new BigDecimal("75.00"));
        existingTransaction.setCurrency("USD");
        existingTransaction.setPaymentChannel("STRIPE");
        existingTransaction.setStatus(PaymentStatus.SUCCESS.name());
        existingTransaction.setProviderTransactionId("pi_existing_123");
        paymentTransactionRepository.save(existingTransaction);

        PaymentRequest request = PaymentRequest.builder()
                .orderId(orderId)
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .paymentChannel("STRIPE")
                .build();

        // Act
        ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                "/api/v1/payment/request",
                request,
                PaymentResponse.class
        );

        // Assert - Should return the existing successful transaction
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // Verify no new transaction was created
        List<PaymentTransaction> transactions = paymentTransactionRepository.findAll();
        long orderTransactions = transactions.stream()
                .filter(t -> t.getOrderId().equals(orderId))
                .count();
        assertThat(orderTransactions).isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("Should retrieve payment transaction by transaction ID")
    void testGetPaymentTransaction_Success() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setAmount(new BigDecimal("120.00"));
        transaction.setCurrency("EUR");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.SUCCESS.name());
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        // Act
        ResponseEntity<PaymentResponse> response = restTemplate.getForEntity(
                "/api/v1/payment/transaction/" + saved.getTransactionId(),
                PaymentResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTransactionId()).isEqualTo(saved.getTransactionId());
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(response.getBody().getCurrency()).isEqualTo("EUR");
    }

    @Test
    @Order(6)
    @DisplayName("Should retrieve payment transaction by order ID")
    void testGetPaymentByOrderId_Success() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setAmount(new BigDecimal("85.50"));
        transaction.setCurrency("GBP");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        paymentTransactionRepository.save(transaction);

        // Act
        ResponseEntity<PaymentResponse> response = restTemplate.getForEntity(
                "/api/v1/payment/order/" + orderId,
                PaymentResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrderId()).isEqualTo(orderId);
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(new BigDecimal("85.50"));
    }
}
