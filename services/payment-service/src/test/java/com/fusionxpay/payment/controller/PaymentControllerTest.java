package com.fusionxpay.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
public class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private PaymentRequest paymentRequest;
    private PaymentResponse paymentResponse;
    private UUID orderId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
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
    void initiatePayment_Success() throws Exception {
        when(paymentService.initiatePayment(any(PaymentRequest.class))).thenReturn(paymentResponse);

        mockMvc.perform(post("/api/payment/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.redirectUrl").value("https://example.com/checkout"));
    }

    @Test
    void getPaymentStatus_Success() throws Exception {
        when(paymentService.getPaymentTransactionByOrderId(orderId)).thenReturn(paymentResponse);

        mockMvc.perform(get("/api/payment/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }
}
