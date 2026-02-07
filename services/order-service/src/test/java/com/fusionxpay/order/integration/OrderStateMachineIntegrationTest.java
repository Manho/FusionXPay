package com.fusionxpay.order.integration;

import com.fusionxpay.common.test.AbstractIntegrationTest;
import com.fusionxpay.order.model.Order;
import com.fusionxpay.order.repository.OrderRepository;
import com.fusionxpay.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Order state machine transitions.
 * Tests valid and invalid state transitions and database consistency.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderStateMachineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        // Create a test order with NEW status
        testOrder = Order.builder()
                .orderNumber("ORD-TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .userId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(OrderService.NEW)
                .build();
        testOrder = orderRepository.save(testOrder);
    }

    @Test
    @DisplayName("Valid transition: NEW -> PROCESSING -> SUCCESS")
    void testValidTransition_NewToProcessingToSuccess() {
        // Verify initial state
        assertThat(testOrder.getStatus()).isEqualTo(OrderService.NEW);

        // Transition: NEW -> PROCESSING
        var processingResult = orderService.updateOrderStatus(testOrder.getOrderNumber(), OrderService.PROCESSING);
        assertThat(processingResult.getStatus()).isEqualTo(OrderService.PROCESSING);

        // Verify database consistency
        Order dbOrder = orderRepository.findById(testOrder.getOrderId()).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(OrderService.PROCESSING);

        // Transition: PROCESSING -> SUCCESS
        var successResult = orderService.updateOrderStatus(testOrder.getOrderNumber(), OrderService.SUCCESS);
        assertThat(successResult.getStatus()).isEqualTo(OrderService.SUCCESS);

        // Verify database consistency
        dbOrder = orderRepository.findById(testOrder.getOrderId()).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(OrderService.SUCCESS);
    }

    @Test
    @DisplayName("Valid transition: NEW -> PROCESSING -> FAILED")
    void testValidTransition_NewToProcessingToFailed() {
        // Transition: NEW -> PROCESSING
        orderService.updateOrderStatus(testOrder.getOrderNumber(), OrderService.PROCESSING);

        // Transition: PROCESSING -> FAILED
        var failedResult = orderService.updateOrderStatus(testOrder.getOrderNumber(), OrderService.FAILED);
        assertThat(failedResult.getStatus()).isEqualTo(OrderService.FAILED);

        // Verify database consistency
        Order dbOrder = orderRepository.findById(testOrder.getOrderId()).orElseThrow();
        assertThat(dbOrder.getStatus()).isEqualTo(OrderService.FAILED);
    }

    @Test
    @DisplayName("Invalid transition: SUCCESS -> NEW should throw exception")
    void testInvalidTransition_SuccessToNew_ShouldThrowException() {
        // Set up order in SUCCESS state
        testOrder.setStatus(OrderService.SUCCESS);
        orderRepository.save(testOrder);

        // Attempt invalid transition: SUCCESS -> NEW
        assertThatThrownBy(() ->
            orderService.updateOrderStatus(testOrder.getOrderNumber(), OrderService.NEW)
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Invalid status transition");
    }

    @Test
    @DisplayName("Invalid transition: COMPLETED -> PENDING should throw exception")
    void testInvalidTransition_CompletedToPending_ShouldThrowException() {
        // Set up order in SUCCESS (completed) state
        testOrder.setStatus(OrderService.SUCCESS);
        orderRepository.save(testOrder);

        // Attempt invalid transition: SUCCESS -> PROCESSING (similar to COMPLETED -> PENDING)
        assertThatThrownBy(() ->
            orderService.updateOrderStatus(testOrder.getOrderNumber(), OrderService.PROCESSING)
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Invalid status transition");
    }

    @Test
    @DisplayName("Invalid transition: FAILED -> SUCCESS should throw exception")
    void testInvalidTransition_FailedToSuccess_ShouldThrowException() {
        // Set up order in FAILED state
        testOrder.setStatus(OrderService.FAILED);
        orderRepository.save(testOrder);

        // Attempt invalid transition: FAILED -> SUCCESS
        assertThatThrownBy(() ->
            orderService.updateOrderStatus(testOrder.getOrderNumber(), OrderService.SUCCESS)
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Invalid status transition");
    }

    @Test
    @DisplayName("Invalid transition: NEW -> SUCCESS (skipping PROCESSING) should throw exception")
    void testInvalidTransition_NewToSuccess_ShouldThrowException() {
        // Attempt invalid transition: NEW -> SUCCESS (skipping PROCESSING)
        assertThatThrownBy(() ->
            orderService.updateOrderStatus(testOrder.getOrderNumber(), OrderService.SUCCESS)
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("Invalid status transition");
    }

    @Test
    @DisplayName("Database consistency: Status update should persist correctly")
    void testDatabaseConsistency_StatusUpdatePersistsCorrectly() {
        // Transition to PROCESSING
        orderService.updateOrderStatus(testOrder.getOrderNumber(), OrderService.PROCESSING);

        // Fetch fresh from database
        Order freshOrder = orderRepository.findByOrderNumber(testOrder.getOrderNumber()).orElseThrow();

        assertThat(freshOrder.getStatus()).isEqualTo(OrderService.PROCESSING);
        assertThat(freshOrder.getUpdatedAt()).isNotNull();
        assertThat(freshOrder.getUpdatedAt()).isAfterOrEqualTo(freshOrder.getCreatedAt());
    }
}
