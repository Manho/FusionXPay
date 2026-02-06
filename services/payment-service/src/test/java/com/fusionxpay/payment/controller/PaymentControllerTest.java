package com.fusionxpay.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.config.TestConfig;
import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.provider.PaymentProvider;
import com.fusionxpay.payment.provider.PaymentProviderFactory;
import com.fusionxpay.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestConfig.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @MockBean
    private PaymentProviderFactory paymentProviderFactory;

    private PaymentProvider paymentProvider;

    @BeforeEach
    void setUp() {
        paymentProvider = new PaymentControllerStubProvider();
        reset(paymentProviderFactory);
    }

    @AfterEach
    void tearDown() {
        paymentTransactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Initiate payment endpoint returns processing response")
    void initiatePayment_Success() throws Exception {
        UUID orderId = UUID.randomUUID();
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setOrderId(orderId);
        paymentRequest.setAmount(new BigDecimal("100.00"));
        paymentRequest.setCurrency("USD");
        paymentRequest.setPaymentChannel("STRIPE");

        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(paymentProvider);

        mockMvc.perform(post("/api/v1/payment/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.redirectUrl").value("https://example.com/checkout"));
    }

    @Test
    @DisplayName("Get payment by order ID returns stored transaction")
    void getPaymentStatus_Success() throws Exception {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        paymentTransactionRepository.save(transaction);

        mockMvc.perform(get("/api/v1/payment/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("Get payment by transaction ID returns stored transaction")
    void getPaymentTransaction_Success() throws Exception {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(UUID.randomUUID());
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        mockMvc.perform(get("/api/v1/payment/transaction/{transactionId}", saved.getTransactionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(saved.getTransactionId().toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("Get available payment providers returns list")
    void getAvailablePaymentProviders() throws Exception {
        mockMvc.perform(get("/api/v1/payment/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("STRIPE"))
                .andExpect(jsonPath("$[1]").value("PAYPAL"));
    }

    private static class PaymentControllerStubProvider implements PaymentProvider {
        @Override
        public com.fusionxpay.payment.dto.PaymentResponse processPayment(PaymentRequest paymentRequest) {
            return com.fusionxpay.payment.dto.PaymentResponse.builder()
                    .orderId(paymentRequest.getOrderId())
                    .status(PaymentStatus.PROCESSING)
                    .paymentChannel(getProviderName())
                    .redirectUrl("https://example.com/checkout")
                    .providerTransactionId("stub-provider-id")
                    .build();
        }

        @Override
        public boolean validateCallback(String payload, String signature) {
            return true;
        }

        @Override
        public com.fusionxpay.payment.dto.PaymentResponse processCallback(String payload, String signature) {
            return null;
        }

        @Override
        public String getProviderName() {
            return "STRIPE";
        }
    }
}
