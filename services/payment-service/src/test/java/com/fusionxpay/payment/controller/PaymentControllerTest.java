package com.fusionxpay.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.common.model.PaymentStatus;
import feign.FeignException;
import feign.Request;
import com.fusionxpay.payment.client.OrderServiceClient;
import com.fusionxpay.payment.config.TestConfig;
import com.fusionxpay.payment.dto.OrderResponse;
import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.RefundResponse;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.provider.PaymentProvider;
import com.fusionxpay.payment.provider.PaymentProviderFactory;
import com.fusionxpay.payment.provider.ProviderRefundRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "fusionxpay.kafka.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:payment_service_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
@Import(TestConfig.class)
class PaymentControllerTest {

    private static final String MERCHANT_HEADER = "X-Merchant-Id";
    private static final long MERCHANT_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @MockBean
    private PaymentProviderFactory paymentProviderFactory;

    @MockBean
    private OrderServiceClient orderServiceClient;

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
        when(orderServiceClient.getOrderById(MERCHANT_ID, orderId)).thenReturn(ResponseEntity.ok(
                OrderResponse.builder().orderId(orderId).userId(MERCHANT_ID).build()
        ));

        mockMvc.perform(post("/api/v1/payment/request")
                .header(MERCHANT_HEADER, MERCHANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.redirectUrl").value("https://example.com/checkout"));
    }

    @Test
    @DisplayName("Initiate payment returns forbidden when order belongs to another merchant")
    void initiatePayment_ForbiddenForOtherMerchantOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setOrderId(orderId);
        paymentRequest.setAmount(new BigDecimal("100.00"));
        paymentRequest.setCurrency("USD");
        paymentRequest.setPaymentChannel("STRIPE");

        Request request = Request.create(Request.HttpMethod.GET,
                "/api/v1/orders/id/" + orderId,
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        when(orderServiceClient.getOrderById(MERCHANT_ID, orderId))
                .thenThrow(new FeignException.Forbidden("Forbidden", request, null, Map.of()));

        mockMvc.perform(post("/api/v1/payment/request")
                .header(MERCHANT_HEADER, MERCHANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Order does not belong to merchant"));
    }

    @Test
    @DisplayName("Get payment by order ID returns stored transaction")
    void getPaymentStatus_Success() throws Exception {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setMerchantId(MERCHANT_ID);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        paymentTransactionRepository.save(transaction);

        mockMvc.perform(get("/api/v1/payment/order/{orderId}", orderId)
                .header(MERCHANT_HEADER, MERCHANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("Get payment by transaction ID returns stored transaction")
    void getPaymentTransaction_Success() throws Exception {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(UUID.randomUUID());
        transaction.setMerchantId(MERCHANT_ID);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        mockMvc.perform(get("/api/v1/payment/transaction/{transactionId}", saved.getTransactionId())
                .header(MERCHANT_HEADER, MERCHANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(saved.getTransactionId().toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("Search payments returns only the authenticated merchant's payments")
    void searchPayments_ScopedToMerchant() throws Exception {
        PaymentTransaction ownTransaction = new PaymentTransaction();
        ownTransaction.setOrderId(UUID.randomUUID());
        ownTransaction.setMerchantId(MERCHANT_ID);
        ownTransaction.setAmount(new BigDecimal("25.00"));
        ownTransaction.setCurrency("USD");
        ownTransaction.setPaymentChannel("STRIPE");
        ownTransaction.setStatus(PaymentStatus.SUCCESS.name());
        paymentTransactionRepository.save(ownTransaction);

        PaymentTransaction otherTransaction = new PaymentTransaction();
        otherTransaction.setOrderId(UUID.randomUUID());
        otherTransaction.setMerchantId(999L);
        otherTransaction.setAmount(new BigDecimal("30.00"));
        otherTransaction.setCurrency("USD");
        otherTransaction.setPaymentChannel("PAYPAL");
        otherTransaction.setStatus(PaymentStatus.SUCCESS.name());
        paymentTransactionRepository.save(otherTransaction);

        mockMvc.perform(get("/api/v1/payment/search")
                .header(MERCHANT_HEADER, MERCHANT_ID)
                .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments").isArray())
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].orderId").value(ownTransaction.getOrderId().toString()))
                .andExpect(jsonPath("$.payments[0].paymentChannel").value("STRIPE"));
    }

    @Test
    @DisplayName("Get payment by transaction ID returns not found for another merchant")
    void getPaymentTransaction_NotFoundForOtherMerchant() throws Exception {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(UUID.randomUUID());
        transaction.setMerchantId(999L);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        mockMvc.perform(get("/api/v1/payment/transaction/{transactionId}", saved.getTransactionId())
                .header(MERCHANT_HEADER, MERCHANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errorMessage").value("Payment transaction not found"));
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
        public RefundResponse processRefund(ProviderRefundRequest refundRequest) {
            throw new UnsupportedOperationException("Refunds are not implemented in this test stub");
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
