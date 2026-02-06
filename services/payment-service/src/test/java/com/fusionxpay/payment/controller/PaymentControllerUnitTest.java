package com.fusionxpay.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class PaymentControllerUnitTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private ObjectMapper objectMapper;
    private PaymentRequest paymentRequest;
    private PaymentResponse paymentResponse;
    private UUID orderId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController).build();
        objectMapper = new ObjectMapper();

        orderId = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        // Setup payment request
        paymentRequest = new PaymentRequest();
        paymentRequest.setOrderId(orderId);
        paymentRequest.setAmount(new BigDecimal("100.00"));
        paymentRequest.setCurrency("USD");
        paymentRequest.setPaymentChannel("STRIPE");

        // Setup payment response
        paymentResponse = PaymentResponse.builder()
                .transactionId(transactionId)
                .orderId(orderId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentChannel("STRIPE")
                .status(PaymentStatus.PROCESSING)
                .redirectUrl("https://example.com/checkout")
                .build();
    }

    @Test
    void testGetAvailablePaymentProviders() throws Exception {
        when(paymentService.getAvailablePaymentProviders()).thenReturn(Arrays.asList("STRIPE", "PAYPAL"));

        mockMvc.perform(get("/api/v1/payment/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]").value("STRIPE"))
                .andExpect(jsonPath("$[1]").value("PAYPAL"));
    }

    @Test
    void testInitiatePayment() throws Exception {
        when(paymentService.initiatePayment(any(PaymentRequest.class))).thenReturn(paymentResponse);

        mockMvc.perform(post("/api/v1/payment/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.redirectUrl").value("https://example.com/checkout"));
    }

    @Test
    void testGetPaymentStatus() throws Exception {
        when(paymentService.getPaymentTransactionByOrderId(orderId)).thenReturn(paymentResponse);

        mockMvc.perform(get("/api/v1/payment/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }
}
