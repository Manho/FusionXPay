package com.fusionxpay.payment.service;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.config.TestConfig;
import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.event.OrderEventProducer;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.provider.PaymentProvider;
import com.fusionxpay.payment.provider.PaymentProviderFactory;
import com.fusionxpay.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(TestConfig.class)
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private OrderEventProducer orderEventProducer;

    @MockBean
    private PaymentProviderFactory paymentProviderFactory;

    private PaymentProvider paymentProvider;

    @BeforeEach
    void setUp() {
        paymentProvider = new StubPaymentProvider();
        reset(orderEventProducer, paymentProviderFactory);
    }

    @AfterEach
    void tearDown() {
        paymentTransactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Initiate payment persists transaction and sends event")
    void initiatePayment_Success() {
        UUID orderId = UUID.randomUUID();
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setOrderId(orderId);
        paymentRequest.setAmount(new BigDecimal("100.00"));
        paymentRequest.setCurrency("USD");
        paymentRequest.setPaymentChannel("STRIPE");

        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(paymentProvider);

        PaymentResponse response = paymentService.initiatePayment(paymentRequest);

        assertNotNull(response);
        assertEquals(orderId, response.getOrderId());
        assertEquals(PaymentStatus.PROCESSING, response.getStatus());
        assertNotNull(response.getTransactionId());

        PaymentTransaction saved = paymentTransactionRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(PaymentStatus.PROCESSING.name(), saved.getStatus());
        assertNotNull(saved.getProviderTransactionId());

        verify(orderEventProducer).sendPaymentStatusUpdate(eq(orderId), any(UUID.class), eq(PaymentStatus.PROCESSING));
    }

    @Test
    @DisplayName("Initiate payment returns existing PROCESSING transaction")
    void initiatePayment_ExistingProcessing() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setAmount(new BigDecimal("50.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());
        paymentTransactionRepository.save(transaction);

        PaymentRequest request = new PaymentRequest();
        request.setOrderId(orderId);
        request.setAmount(new BigDecimal("50.00"));
        request.setCurrency("USD");
        request.setPaymentChannel("STRIPE");

        PaymentResponse response = paymentService.initiatePayment(request);

        assertNotNull(response);
        assertEquals(orderId, response.getOrderId());
        assertEquals(PaymentStatus.PROCESSING, response.getStatus());

        verifyNoInteractions(paymentProviderFactory);
        verifyNoInteractions(orderEventProducer);
    }

    @Test
    @DisplayName("Handle callback updates transaction and sends event")
    void handleCallback_Success() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setAmount(new BigDecimal("75.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        paymentTransactionRepository.save(transaction);

        PaymentProvider callbackProvider = new CallbackProvider(orderId, PaymentStatus.SUCCESS);
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(callbackProvider);

        boolean result = paymentService.handleCallback("payload", "signature", "STRIPE");

        assertTrue(result);
        PaymentTransaction updated = paymentTransactionRepository.findByOrderId(orderId).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS.name(), updated.getStatus());
        verify(orderEventProducer).sendPaymentStatusUpdate(orderId, updated.getTransactionId(), PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("Handle callback returns false on invalid signature")
    void handleCallback_InvalidSignature() {
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(new InvalidSignatureProvider());

        boolean result = paymentService.handleCallback("payload", "signature", "STRIPE");

        assertFalse(result);
        verifyNoInteractions(orderEventProducer);
    }

    private static class StubPaymentProvider implements PaymentProvider {
        @Override
        public PaymentResponse processPayment(PaymentRequest paymentRequest) {
            return PaymentResponse.builder()
                    .transactionId(UUID.randomUUID())
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
        public PaymentResponse processCallback(String payload, String signature) {
            return null;
        }

        @Override
        public String getProviderName() {
            return "STRIPE";
        }
    }

    private static class CallbackProvider implements PaymentProvider {
        private final UUID orderId;
        private final PaymentStatus status;

        private CallbackProvider(UUID orderId, PaymentStatus status) {
            this.orderId = orderId;
            this.status = status;
        }

        @Override
        public PaymentResponse processPayment(PaymentRequest paymentRequest) {
            return null;
        }

        @Override
        public boolean validateCallback(String payload, String signature) {
            return true;
        }

        @Override
        public PaymentResponse processCallback(String payload, String signature) {
            return PaymentResponse.builder()
                    .orderId(orderId)
                    .status(status)
                    .paymentChannel(getProviderName())
                    .build();
        }

        @Override
        public String getProviderName() {
            return "STRIPE";
        }
    }

    private static class InvalidSignatureProvider implements PaymentProvider {
        @Override
        public PaymentResponse processPayment(PaymentRequest paymentRequest) {
            return null;
        }

        @Override
        public boolean validateCallback(String payload, String signature) {
            return false;
        }

        @Override
        public PaymentResponse processCallback(String payload, String signature) {
            return null;
        }

        @Override
        public String getProviderName() {
            return "STRIPE";
        }
    }
}
