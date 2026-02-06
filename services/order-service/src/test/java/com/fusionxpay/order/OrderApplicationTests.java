package com.fusionxpay.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.order.config.TestConfig;
import com.fusionxpay.order.controller.OrderController;
import com.fusionxpay.order.dto.OrderRequest;
import com.fusionxpay.order.dto.OrderResponse;
import com.fusionxpay.order.model.Order;
import com.fusionxpay.order.repository.OrderRepository;
import com.fusionxpay.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestConfig.class)
@Transactional
class OrderApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderController orderController;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private OrderRequest orderRequest;
    private UUID testOrderId;

    @BeforeEach
    void setUp() {
        // Create a test order request
        orderRequest = OrderRequest.builder()
                .userId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();
    }

    @Test
    @DisplayName("Context loads and all required beans are available")
    void contextLoads() {
        assertNotNull(mockMvc);
        assertNotNull(objectMapper);
        assertNotNull(orderController);
        assertNotNull(orderService);
        assertNotNull(orderRepository);
    }

    @Test
    @DisplayName("Create order endpoint returns correct response")
    void createOrderEndpoint() throws Exception {
        // Perform the request
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(orderRequest.getUserId()))
                .andExpect(jsonPath("$.amount").value(orderRequest.getAmount().doubleValue()))
                .andExpect(jsonPath("$.currency").value(orderRequest.getCurrency()))
                .andExpect(jsonPath("$.status").value(OrderService.NEW))
                .andReturn();

        // Extract the order ID for later tests
        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                OrderResponse.class);
        testOrderId = response.getOrderId();
        assertNotNull(testOrderId, "Order ID should not be null");
    }

    @Test
    @DisplayName("Get order by ID endpoint returns correct order")
    void getOrderByIdEndpoint() throws Exception {
        // First create an order
        OrderResponse createdOrder = orderService.createOrder(orderRequest);
        assertNotNull(createdOrder.getOrderId());

        // Then retrieve it by ID
        mockMvc.perform(get("/api/v1/orders/id/" + createdOrder.getOrderId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(createdOrder.getOrderId().toString()))
                .andExpect(jsonPath("$.orderNumber").value(createdOrder.getOrderNumber()))
                .andExpect(jsonPath("$.userId").value(orderRequest.getUserId()))
                .andExpect(jsonPath("$.amount").value(orderRequest.getAmount().doubleValue()))
                .andExpect(jsonPath("$.currency").value(orderRequest.getCurrency()))
                .andExpect(jsonPath("$.status").value(OrderService.NEW));
    }

    @Test
    @DisplayName("Get order by number endpoint returns correct order")
    void getOrderByNumberEndpoint() throws Exception {
        // First create an order
        OrderResponse createdOrder = orderService.createOrder(orderRequest);
        assertNotNull(createdOrder.getOrderNumber());

        // Then retrieve it by order number
        mockMvc.perform(get("/api/v1/orders/" + createdOrder.getOrderNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(createdOrder.getOrderId().toString()))
                .andExpect(jsonPath("$.orderNumber").value(createdOrder.getOrderNumber()))
                .andExpect(jsonPath("$.userId").value(orderRequest.getUserId()))
                .andExpect(jsonPath("$.amount").value(orderRequest.getAmount().doubleValue()))
                .andExpect(jsonPath("$.currency").value(orderRequest.getCurrency()))
                .andExpect(jsonPath("$.status").value(OrderService.NEW));
    }

    @Test
    @DisplayName("Order status update works correctly")
    void updateOrderStatus() throws Exception {
        // First create an order
        OrderResponse createdOrder = orderService.createOrder(orderRequest);
        assertNotNull(createdOrder.getOrderNumber());

        // Update the status
        OrderResponse updatedOrder = orderService.updateOrderStatus(createdOrder.getOrderNumber(), OrderService.PROCESSING);

        // Verify the status was updated
        assertEquals(OrderService.PROCESSING, updatedOrder.getStatus());

        // Verify the order in the database has the updated status
        Order order = orderRepository.findByOrderNumber(createdOrder.getOrderNumber()).orElse(null);
        assertNotNull(order);
        assertEquals(OrderService.PROCESSING, order.getStatus());
    }

    @Test
    @DisplayName("Order persistence works correctly")
    void orderPersistence() {
        // Create an order through the service
        OrderResponse createdOrder = orderService.createOrder(orderRequest);

        // Verify it exists in the repository
        Order savedOrder = orderRepository.findById(createdOrder.getOrderId()).orElse(null);

        assertNotNull(savedOrder);
        assertEquals(createdOrder.getOrderId(), savedOrder.getOrderId());
        assertEquals(createdOrder.getOrderNumber(), savedOrder.getOrderNumber());
        assertEquals(createdOrder.getUserId(), savedOrder.getUserId());
        assertEquals(0, createdOrder.getAmount().compareTo(savedOrder.getAmount()));
        assertEquals(createdOrder.getCurrency(), savedOrder.getCurrency());
        assertEquals(createdOrder.getStatus(), savedOrder.getStatus());
    }
}
