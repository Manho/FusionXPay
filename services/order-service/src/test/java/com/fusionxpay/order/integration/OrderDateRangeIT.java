package com.fusionxpay.order.integration;

import com.fusionxpay.common.test.AbstractIntegrationTest;
import com.fusionxpay.order.dto.OrderPageResponse;
import com.fusionxpay.order.model.Order;
import com.fusionxpay.order.repository.OrderRepository;
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
class OrderDateRangeIT extends AbstractIntegrationTest {

    private static final long MERCHANT_A = 11L;
    private static final long MERCHANT_B = 22L;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("eureka.client.enabled", () -> false);
        registry.add("spring.cloud.discovery.enabled", () -> false);
    }

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Order date range honors merchant header and same-day filters")
    void getOrders_SameDayFilterScopedToMerchant() {
        LocalDate today = LocalDate.now();
        Order expected = createOrder(
                MERCHANT_A,
                "NEW",
                today.atTime(9, 0)
        );
        createOrder(
                MERCHANT_A,
                "NEW",
                today.minusDays(2).atTime(15, 0)
        );
        createOrder(
                MERCHANT_B,
                "NEW",
                today.atTime(13, 30)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Merchant-Id", String.valueOf(MERCHANT_A));

        ResponseEntity<OrderPageResponse> response = restTemplate.exchange(
                "/api/v1/orders?from=" + today + "&to=" + today,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                OrderPageResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        assertThat(response.getBody().getOrders()).hasSize(1);
        assertThat(response.getBody().getOrders().get(0).getOrderId()).isEqualTo(expected.getOrderId());
        assertThat(response.getBody().getOrders().get(0).getUserId()).isEqualTo(MERCHANT_A);
    }

    @Test
    @DisplayName("Order date range supports legacy startDate alias and open-ended filters")
    void getOrders_LegacyAliasAndOpenEndedRange() {
        LocalDate today = LocalDate.now();
        Order older = createOrder(
                MERCHANT_A,
                "PROCESSING",
                today.minusDays(5).atTime(10, 0)
        );
        Order newer = createOrder(
                MERCHANT_A,
                "PROCESSING",
                today.minusDays(1).atTime(10, 0)
        );
        createOrder(
                MERCHANT_A,
                "FAILED",
                today.minusDays(1).atTime(16, 0)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Merchant-Id", String.valueOf(MERCHANT_A));

        ResponseEntity<OrderPageResponse> response = restTemplate.exchange(
                "/api/v1/orders?status=PROCESSING&startDate=" + today.minusDays(2),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                OrderPageResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalElements()).isEqualTo(1);
        assertThat(response.getBody().getOrders()).hasSize(1);
        assertThat(response.getBody().getOrders().get(0).getOrderId()).isEqualTo(newer.getOrderId());
        assertThat(response.getBody().getOrders().get(0).getOrderId()).isNotEqualTo(older.getOrderId());
    }

    private Order createOrder(long merchantId, String status, LocalDateTime createdAt) {
        Order order = Order.builder()
                .orderNumber("ORD-IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .userId(merchantId)
                .amount(new BigDecimal("42.00"))
                .currency("USD")
                .status(status)
                .build();

        Order saved = orderRepository.save(order);
        jdbcTemplate.update(
                "UPDATE orders SET created_at = ?, updated_at = ? WHERE order_number = ?",
                Timestamp.valueOf(createdAt),
                Timestamp.valueOf(createdAt),
                saved.getOrderNumber()
        );
        saved.setCreatedAt(createdAt);
        saved.setUpdatedAt(createdAt);
        return saved;
    }
}
