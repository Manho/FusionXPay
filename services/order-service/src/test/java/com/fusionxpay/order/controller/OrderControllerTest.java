package com.fusionxpay.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.order.dto.OrderRequest;
import com.fusionxpay.order.dto.OrderResponse;
import com.fusionxpay.order.repository.OrderRepository;
import com.fusionxpay.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Create order endpoint returns created order")
    void createOrder_Success() throws Exception {
        OrderRequest orderRequest = OrderRequest.builder()
                .userId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value(OrderService.NEW));
    }

    @Test
    @DisplayName("Get order by ID returns order")
    void getOrderById_Success() throws Exception {
        OrderResponse createdOrder = orderService.createOrder(OrderRequest.builder()
                .userId(2L)
                .amount(new BigDecimal("20.00"))
                .currency("USD")
                .build());

        mockMvc.perform(get("/api/v1/orders/id/" + createdOrder.getOrderId())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(createdOrder.getOrderId().toString()))
                .andExpect(jsonPath("$.orderNumber").value(createdOrder.getOrderNumber()));
    }

    @Test
    @DisplayName("Get order by ID returns 404 when missing")
    void getOrderById_NotFound() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/orders/id/" + orderId)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found with orderId: " + orderId));
    }

    @Test
    @DisplayName("Get order by number returns order")
    void getOrderByNumber_Success() throws Exception {
        OrderResponse createdOrder = orderService.createOrder(OrderRequest.builder()
                .userId(3L)
                .amount(new BigDecimal("15.00"))
                .currency("USD")
                .build());

        mockMvc.perform(get("/api/v1/orders/" + createdOrder.getOrderNumber())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(createdOrder.getOrderId().toString()))
                .andExpect(jsonPath("$.orderNumber").value(createdOrder.getOrderNumber()));
    }

    @Test
    @DisplayName("Get order by number returns 404 when missing")
    void getOrderByNumber_NotFound() throws Exception {
        String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        mockMvc.perform(get("/api/v1/orders/" + orderNumber)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found with number: " + orderNumber));
    }

    @Test
    @DisplayName("Create order returns 400 when validation fails")
    void createOrder_ValidationFailure() throws Exception {
        OrderRequest invalidRequest = OrderRequest.builder()
                .userId(null)
                .amount(new BigDecimal("-1.00"))
                .currency("")
                .build();

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
