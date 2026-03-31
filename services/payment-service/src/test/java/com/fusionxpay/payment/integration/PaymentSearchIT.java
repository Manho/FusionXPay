package com.fusionxpay.payment.integration;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import com.fusionxpay.payment.dto.PaymentPageResponse;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentSearchIT extends AbstractIntegrationTest {

    private static final long MERCHANT_A = 101L;
    private static final long MERCHANT_B = 202L;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("eureka.client.enabled", () -> false);
        registry.add("spring.cloud.discovery.enabled", () -> false);
    }

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Search payments returns current merchant records only for date range and status filter")
    void searchPayments_ScopedByMerchantStatusAndDate() {
        LocalDate today = LocalDate.now();
        PaymentTransaction expected = createTransaction(
                MERCHANT_A,
                UUID.randomUUID(),
                PaymentStatus.PROCESSING.name(),
                today.atTime(10, 15)
        );
        createTransaction(
                MERCHANT_A,
                UUID.randomUUID(),
                PaymentStatus.FAILED.name(),
                today.atTime(11, 0)
        );
        createTransaction(
                MERCHANT_B,
                UUID.randomUUID(),
                PaymentStatus.PROCESSING.name(),
                today.atTime(12, 0)
        );
        createTransaction(
                MERCHANT_A,
                UUID.randomUUID(),
                PaymentStatus.PROCESSING.name(),
                today.minusDays(3).atTime(9, 30)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Merchant-Id", String.valueOf(MERCHANT_A));

        ResponseEntity<PaymentPageResponse> response = restTemplate.exchange(
                "/api/v1/payment/search?status=PROCESSING&from=" + today + "&to=" + today,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PaymentPageResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        assertThat(response.getBody().getPayments()).hasSize(1);
        assertThat(response.getBody().getPayments().get(0).getTransactionId()).isEqualTo(expected.getTransactionId());
        assertThat(response.getBody().getPayments().get(0).getOrderId()).isEqualTo(expected.getOrderId());
        assertThat(response.getBody().getPayments().get(0).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    private PaymentTransaction createTransaction(
            long merchantId,
            UUID orderId,
            String status,
            LocalDateTime createdAt
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setMerchantId(merchantId);
        transaction.setAmount(new BigDecimal("19.99"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(status);
        transaction.setProviderTransactionId("provider-" + UUID.randomUUID());

        PaymentTransaction saved = paymentTransactionRepository.save(transaction);
        jdbcTemplate.update(
                "UPDATE payment_transactions SET created_at = ?, updated_at = ? WHERE provider_transaction_id = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt),
                saved.getProviderTransactionId()
        );
        saved.setCreatedAt(createdAt);
        saved.setUpdatedAt(createdAt);
        return saved;
    }
}
