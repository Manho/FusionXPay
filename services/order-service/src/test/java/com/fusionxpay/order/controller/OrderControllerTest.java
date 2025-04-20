package com.fusionxpay.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fusionxpay.order.dto.OrderRequest;
import com.fusionxpay.order.dto.OrderResponse;
import com.fusionxpay.order.exception.OrderNotFoundException;
import com.fusionxpay.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private OrderRequest orderRequest;
    private OrderResponse orderResponse;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new RestExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        orderId = UUID.randomUUID();

        // Setup order request
        orderRequest = OrderRequest.builder()
                .userId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        // Setup order response
        orderResponse = OrderResponse.builder()
                .orderId(orderId)
                .orderNumber("ORD-12345678")
                .userId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(OrderService.NEW)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // Exception handler for the controller
    private static class RestExceptionHandler extends ResponseEntityExceptionHandler {}

    @Test
    @DisplayName("Test create order success")
    void createOrder_Success() throws Exception {
        when(orderService.createOrder(any(OrderRequest.class))).thenReturn(orderResponse);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.orderNumber").value("ORD-12345678"))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value(OrderService.NEW));
    }

    @Test
    @DisplayName("Test get order by ID success")
    void getOrderById_Success() throws Exception {
        when(orderService.getOrderById(orderId)).thenReturn(orderResponse);

        mockMvc.perform(get("/api/orders/id/" + orderId)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.orderNumber").value("ORD-12345678"));
    }

    @Test
    @DisplayName("Test get order by ID not found")
    void getOrderById_NotFound() throws Exception {
        when(orderService.getOrderById(orderId)).thenThrow(new OrderNotFoundException("Order not found with orderId: " + orderId));

        mockMvc.perform(get("/api/orders/id/" + orderId)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found with orderId: " + orderId));
    }

    @Test
    @DisplayName("Test get order by number success")
    void getOrderByNumber_Success() throws Exception {
        when(orderService.getOrderByNumber("ORD-12345678")).thenReturn(orderResponse);

        mockMvc.perform(get("/api/orders/ORD-12345678")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.orderNumber").value("ORD-12345678"));
    }

    @Test
    @DisplayName("Test get order by number not found")
    void getOrderByNumber_NotFound() throws Exception {
        when(orderService.getOrderByNumber("ORD-12345678")).thenThrow(new OrderNotFoundException("Order not found with number: ORD-12345678"));

        mockMvc.perform(get("/api/orders/ORD-12345678")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found with number: ORD-12345678"));
    }
}
