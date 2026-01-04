package com.fusionxpay.order.service;

import com.fusionxpay.order.dto.OrderRequest;
import com.fusionxpay.order.dto.OrderResponse;
import com.fusionxpay.order.exception.OrderNotFoundException;
import com.fusionxpay.order.model.Order;
import com.fusionxpay.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Create order persists and returns response")
    void createOrder_Success() {
        OrderRequest orderRequest = OrderRequest.builder()
                .userId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        OrderResponse response = orderService.createOrder(orderRequest);

        assertNotNull(response);
        assertNotNull(response.getOrderId());
        assertNotNull(response.getOrderNumber());
        assertEquals(OrderService.NEW, response.getStatus());

        Order savedOrder = orderRepository.findById(response.getOrderId()).orElseThrow();
        assertEquals(response.getOrderNumber(), savedOrder.getOrderNumber());
    }

    @Test
    @DisplayName("Get order by ID returns order")
    void getOrderById_Success() {
        OrderResponse createdOrder = orderService.createOrder(OrderRequest.builder()
                .userId(2L)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .build());

        OrderResponse response = orderService.getOrderById(createdOrder.getOrderId());

        assertNotNull(response);
        assertEquals(createdOrder.getOrderId(), response.getOrderId());
    }

    @Test
    @DisplayName("Get order by ID throws when missing")
    void getOrderById_NotFound() {
        UUID nonExistentId = UUID.randomUUID();
        Exception exception = assertThrows(OrderNotFoundException.class, () ->
            orderService.getOrderById(nonExistentId)
        );

        assertTrue(exception.getMessage().contains("Order not found with orderId"));
    }

    @Test
    @DisplayName("Get order by number returns order")
    void getOrderByNumber_Success() {
        OrderResponse createdOrder = orderService.createOrder(OrderRequest.builder()
                .userId(3L)
                .amount(new BigDecimal("12.00"))
                .currency("USD")
                .build());

        OrderResponse response = orderService.getOrderByNumber(createdOrder.getOrderNumber());

        assertNotNull(response);
        assertEquals(createdOrder.getOrderNumber(), response.getOrderNumber());
    }

    @Test
    @DisplayName("Update order status succeeds with valid transition")
    void updateOrderStatus_Success() {
        OrderResponse createdOrder = orderService.createOrder(OrderRequest.builder()
                .userId(4L)
                .amount(new BigDecimal("55.00"))
                .currency("USD")
                .build());

        OrderResponse response = orderService.updateOrderStatus(createdOrder.getOrderNumber(), OrderService.PROCESSING);

        assertNotNull(response);
        assertEquals(OrderService.PROCESSING, response.getStatus());
    }

    @Test
    @DisplayName("Update order status rejects invalid transition")
    void updateOrderStatus_InvalidTransition() {
        OrderResponse createdOrder = orderService.createOrder(OrderRequest.builder()
                .userId(5L)
                .amount(new BigDecimal("55.00"))
                .currency("USD")
                .build());

        Exception exception = assertThrows(IllegalStateException.class, () ->
            orderService.updateOrderStatus(createdOrder.getOrderNumber(), OrderService.SUCCESS)
        );

        assertTrue(exception.getMessage().contains("Invalid status transition"));
    }

    @Test
    @DisplayName("Update order status throws when order missing")
    void updateOrderStatus_OrderNotFound() {
        Exception exception = assertThrows(OrderNotFoundException.class, () ->
            orderService.updateOrderStatus("NON_EXISTENT", OrderService.PROCESSING)
        );

        assertTrue(exception.getMessage().contains("Order not found with number"));
    }

    @Test
    @DisplayName("Update order status by ID succeeds with valid transition")
    void updateOrderStatusById_Success() {
        OrderResponse createdOrder = orderService.createOrder(OrderRequest.builder()
                .userId(6L)
                .amount(new BigDecimal("80.00"))
                .currency("USD")
                .build());

        OrderResponse response = orderService.updateOrderStatusById(
                createdOrder.getOrderId(),
                OrderService.PROCESSING,
                "Payment processing");

        assertNotNull(response);
        assertEquals(OrderService.PROCESSING, response.getStatus());
    }

    @Test
    @DisplayName("Update order status by ID rejects invalid transition")
    void updateOrderStatusById_InvalidTransition() {
        OrderResponse createdOrder = orderService.createOrder(OrderRequest.builder()
                .userId(7L)
                .amount(new BigDecimal("80.00"))
                .currency("USD")
                .build());

        Exception exception = assertThrows(IllegalStateException.class, () ->
            orderService.updateOrderStatusById(createdOrder.getOrderId(), OrderService.SUCCESS, "Direct to success")
        );

        assertTrue(exception.getMessage().contains("Invalid status transition"));
    }

    @Test
    @DisplayName("Update order status by ID throws when missing")
    void updateOrderStatusById_OrderNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        Exception exception = assertThrows(OrderNotFoundException.class, () ->
            orderService.updateOrderStatusById(nonExistentId, OrderService.PROCESSING, "Processing message")
        );

        assertTrue(exception.getMessage().contains("Order not found with orderId"));
    }
}
