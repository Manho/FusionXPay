package com.fusionxpay.order.service;

import com.fusionxpay.order.dto.OrderRequest;
import com.fusionxpay.order.dto.OrderResponse;
import com.fusionxpay.order.event.OrderEvent;
import com.fusionxpay.order.event.OrderEventPublisher;
import com.fusionxpay.order.exception.OrderNotFoundException;
import com.fusionxpay.order.model.Order;
import com.fusionxpay.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    private OrderRequest orderRequest;
    private Order order;

    @BeforeEach
    void setUp() {
        orderRequest = OrderRequest.builder()
                .userId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        order = Order.builder()
                .id(1L)
                .orderNumber("ORD-12345678")
                .userId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(OrderService.NEW)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Test createOrder success")
    void createOrder_Success() {
        // Arrange
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        doNothing().when(eventPublisher).publishOrderEvent(any(OrderEvent.class));

        // Act
        OrderResponse response = orderService.createOrder(orderRequest);

        // Assert
        assertNotNull(response);
        assertEquals(order.getId(), response.getId());
        assertEquals(order.getOrderNumber(), response.getOrderNumber());
        assertEquals(order.getUserId(), response.getUserId());
        assertEquals(order.getAmount(), response.getAmount());
        assertEquals(order.getCurrency(), response.getCurrency());
        assertEquals(order.getStatus(), response.getStatus());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());

        // Verify event publication using ArgumentCaptor
        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(eventPublisher).publishOrderEvent(eventCaptor.capture());
        OrderEvent capturedEvent = eventCaptor.getValue();
        assertEquals("ORDER_CREATED", capturedEvent.getEventType());
        assertEquals(order.getOrderNumber(), capturedEvent.getOrderNumber());
    }

    @Test
    @DisplayName("Test getOrderById success")
    void getOrderById_Success() {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(order.getId());

        assertNotNull(response);
        assertEquals(order.getId(), response.getId());
        verify(orderRepository).findById(order.getId());
    }

    @Test
    @DisplayName("Test getOrderById not found")
    void getOrderById_NotFound() {
        Long nonExistentId = 999L;
        when(orderRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        Exception exception = assertThrows(OrderNotFoundException.class, () ->
            orderService.getOrderById(nonExistentId)
        );

        assertTrue(exception.getMessage().contains("Order not found with ID"));
    }

    @Test
    @DisplayName("Test getOrderByNumber success")
    void getOrderByNumber_Success() {
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderByNumber(order.getOrderNumber());

        assertNotNull(response);
        assertEquals(order.getOrderNumber(), response.getOrderNumber());
        verify(orderRepository).findByOrderNumber(order.getOrderNumber());
    }

    @Test
    @DisplayName("Test updateOrderStatus success with valid transition")
    void updateOrderStatus_Success() {
        // Given the order is in NEW state, a valid transition is to PROCESSING
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(eventPublisher).publishOrderEvent(any(OrderEvent.class));

        OrderResponse response = orderService.updateOrderStatus(order.getOrderNumber(), OrderService.PROCESSING);

        assertNotNull(response);
        assertEquals(OrderService.PROCESSING, response.getStatus());

        // Verify that an update event is published
        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(eventPublisher).publishOrderEvent(eventCaptor.capture());
        OrderEvent capturedEvent = eventCaptor.getValue();
        assertEquals("ORDER_UPDATED", capturedEvent.getEventType());
    }

    @Test
    @DisplayName("Test updateOrderStatus with invalid transition")
    void updateOrderStatus_InvalidTransition() {
        // Given the order is in NEW state, an invalid transition (e.g., to SUCCESS) should throw an exception
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));

        Exception exception = assertThrows(IllegalStateException.class, () ->
            orderService.updateOrderStatus(order.getOrderNumber(), OrderService.SUCCESS)
        );

        assertTrue(exception.getMessage().contains("Invalid status transition"));
    }

    @Test
    @DisplayName("Test updateOrderStatus order not found")
    void updateOrderStatus_OrderNotFound() {
        when(orderRepository.findByOrderNumber("NON_EXISTENT")).thenReturn(Optional.empty());

        Exception exception = assertThrows(OrderNotFoundException.class, () ->
            orderService.updateOrderStatus("NON_EXISTENT", OrderService.PROCESSING)
        );

        assertTrue(exception.getMessage().contains("Order not found with number"));
    }
}