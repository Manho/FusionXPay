package com.fusionxpay.payment;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.config.TestConfig;
import com.fusionxpay.payment.controller.PaymentController;
import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.provider.PaymentProvider;
import com.fusionxpay.payment.provider.PaymentProviderFactory;
import com.fusionxpay.payment.repository.PaymentTransactionRepository;
import com.fusionxpay.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(TestConfig.class)
class PaymentApplicationTests {

    @Autowired
    private PaymentController paymentController;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentProviderFactory paymentProviderFactory;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    // RedisTemplate is already mocked in TestConfig

    // OrderEventProducer is already mocked in TestConfig

    @Test
    void contextLoads() {
        // Verify that the context loads and key beans are available
        assertNotNull(paymentController);
        assertNotNull(paymentService);
        assertNotNull(paymentProviderFactory);
        assertNotNull(paymentTransactionRepository);
    }

    @Test
    void testPaymentProvidersAvailable() {
        // Test that the payment service returns the available payment providers
        var providers = paymentService.getAvailablePaymentProviders();
        assertNotNull(providers);
        assert(providers.contains("STRIPE"));
        assert(providers.contains("PAYPAL"));
    }

    @Test
    void testPaymentProviderFactory() {
        // Test that the payment provider factory returns the correct providers
        PaymentProvider stripeProvider = paymentProviderFactory.getProvider("STRIPE");
        PaymentProvider paypalProvider = paymentProviderFactory.getProvider("PAYPAL");

        assertNotNull(stripeProvider);
        assertNotNull(paypalProvider);
        assert("STRIPE".equalsIgnoreCase(stripeProvider.getProviderName()));
        assert("PAYPAL".equalsIgnoreCase(paypalProvider.getProviderName()));
    }

    @Test
    void testGetPaymentTransactionByOrderId_NotFound() {
        // Given
        UUID orderId = UUID.randomUUID();
        when(paymentTransactionRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        // When
        PaymentResponse response = paymentService.getPaymentTransactionByOrderId(orderId);

        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.NOT_FOUND, response.getStatus());
        assertEquals(orderId, response.getOrderId());
        verify(paymentTransactionRepository).findByOrderId(orderId);
    }

    @Test
    void testGetPaymentTransactionByOrderId_Found() {
        // Given
        UUID orderId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setTransactionId(transactionId);
        transaction.setOrderId(orderId);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.SUCCESS.name());

        when(paymentTransactionRepository.findByOrderId(orderId)).thenReturn(Optional.of(transaction));

        // When
        PaymentResponse response = paymentService.getPaymentTransactionByOrderId(orderId);

        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals(orderId, response.getOrderId());
        assertEquals(transactionId, response.getTransactionId());
        verify(paymentTransactionRepository).findByOrderId(orderId);
    }

    @Test
    void testInitiatePayment_NewTransaction() {
        // Given
        UUID orderId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        PaymentRequest request = new PaymentRequest();
        request.setOrderId(orderId);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        request.setPaymentChannel("STRIPE");

        PaymentTransaction savedTransaction = new PaymentTransaction();
        savedTransaction.setTransactionId(transactionId);
        savedTransaction.setOrderId(orderId);
        savedTransaction.setAmount(new BigDecimal("100.00"));
        savedTransaction.setCurrency("USD");
        savedTransaction.setPaymentChannel("STRIPE");
        savedTransaction.setStatus(PaymentStatus.INITIATED.name());

        PaymentProvider mockProvider = mock(PaymentProvider.class);
        PaymentResponse providerResponse = PaymentResponse.builder()
                .transactionId(transactionId)
                .orderId(orderId)
                .status(PaymentStatus.PROCESSING)
                .redirectUrl("https://example.com/checkout")
                .build();

        when(paymentTransactionRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(mockProvider);
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenReturn(savedTransaction);
        when(mockProvider.processPayment(request)).thenReturn(providerResponse);

        // When
        PaymentResponse response = paymentService.initiatePayment(request);

        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.PROCESSING, response.getStatus());
        assertEquals(orderId, response.getOrderId());
        assertEquals(transactionId, response.getTransactionId());

        // Verify interactions
        verify(paymentTransactionRepository).findByOrderId(orderId);
        verify(paymentProviderFactory).getProvider("STRIPE");
        verify(mockProvider).processPayment(request);
        verify(paymentTransactionRepository, times(2)).save(any(PaymentTransaction.class));

        // Note: In a real test, we would verify the OrderEventProducer was called
        // but since it's mocked in TestConfig, we can't access it directly here
    }
}
