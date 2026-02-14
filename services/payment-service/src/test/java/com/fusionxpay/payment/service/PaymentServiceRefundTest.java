package com.fusionxpay.payment.service;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.dto.RefundRequest;
import com.fusionxpay.payment.dto.RefundResponse;
import com.fusionxpay.payment.dto.paypal.PayPalRefundResponse;
import com.fusionxpay.payment.event.OrderEventProducer;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.model.RefundStatus;
import com.fusionxpay.payment.provider.PayPalProvider;
import com.fusionxpay.payment.provider.PaymentProviderFactory;
import com.fusionxpay.payment.provider.StripeProvider;
import com.fusionxpay.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentService refund functionality
 */
@ExtendWith(MockitoExtension.class)
public class PaymentServiceRefundTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @Mock
    private OrderEventProducer orderEventProducer;

    @Mock
    private StripeProvider stripeProvider;

    @Mock
    private PayPalProvider payPalProvider;

    @InjectMocks
    private PaymentService paymentService;

    private UUID transactionId;
    private PaymentTransaction successfulTransaction;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();

        successfulTransaction = new PaymentTransaction();
        successfulTransaction.setTransactionId(transactionId);
        successfulTransaction.setOrderId(UUID.randomUUID());
        successfulTransaction.setAmount(new BigDecimal("100.00"));
        successfulTransaction.setCurrency("USD");
        successfulTransaction.setPaymentChannel("STRIPE");
        successfulTransaction.setStatus(PaymentStatus.SUCCESS.name());
        successfulTransaction.setProviderTransactionId("pi_test_123");
    }

    // ==================== Stripe Refund Tests ====================

    @Test
    void testInitiateRefund_Stripe_Success() {
        // Given
        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());
        request.setAmount(new BigDecimal("50.00"));
        request.setReason("Customer request");

        when(paymentTransactionRepository.findById(transactionId))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(stripeProvider);

        RefundResponse stripeResponse = RefundResponse.builder()
                .refundId("re_test_123")
                .providerRefundId("re_stripe_123")
                .status(RefundStatus.COMPLETED)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .paymentChannel("STRIPE")
                .build();
        when(stripeProvider.processRefund(eq("pi_test_123"), eq(new BigDecimal("50.00")), eq("Customer request")))
                .thenReturn(stripeResponse);

        // When
        RefundResponse response = paymentService.initiateRefund(request);

        // Then
        assertNotNull(response);
        assertEquals(RefundStatus.COMPLETED, response.getStatus());
        assertEquals(transactionId.toString(), response.getTransactionId());
        // Refund completion is confirmed asynchronously via provider webhooks; do not mark REFUNDED here.
        verify(paymentTransactionRepository, never()).save(any(PaymentTransaction.class));
        assertEquals(PaymentStatus.SUCCESS.name(), successfulTransaction.getStatus());
    }

    @Test
    void testInitiateRefund_Stripe_FullRefund() {
        // Given
        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());
        request.setAmount(null); // Full refund
        request.setReason("Full refund");

        when(paymentTransactionRepository.findById(transactionId))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(stripeProvider);

        RefundResponse stripeResponse = RefundResponse.builder()
                .refundId("re_test_123")
                .status(RefundStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentChannel("STRIPE")
                .build();
        when(stripeProvider.processRefund(eq("pi_test_123"), isNull(), eq("Full refund")))
                .thenReturn(stripeResponse);

        // When
        RefundResponse response = paymentService.initiateRefund(request);

        // Then
        assertNotNull(response);
        assertEquals(RefundStatus.COMPLETED, response.getStatus());
        // Refund completion is confirmed asynchronously via provider webhooks; do not mark REFUNDED here.
        verify(paymentTransactionRepository, never()).save(any(PaymentTransaction.class));
        assertEquals(PaymentStatus.SUCCESS.name(), successfulTransaction.getStatus());
    }

    @Test
    void testInitiateRefund_Stripe_Failed() {
        // Given
        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());
        request.setAmount(new BigDecimal("50.00"));

        when(paymentTransactionRepository.findById(transactionId))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(stripeProvider);

        RefundResponse stripeResponse = RefundResponse.builder()
                .status(RefundStatus.FAILED)
                .errorMessage("Insufficient funds")
                .paymentChannel("STRIPE")
                .build();
        when(stripeProvider.processRefund(anyString(), any(), any()))
                .thenReturn(stripeResponse);

        // When
        RefundResponse response = paymentService.initiateRefund(request);

        // Then
        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        // Transaction should not be marked as refunded
        verify(paymentTransactionRepository, never()).save(argThat(tx ->
                "REFUNDED".equals(tx.getStatus())
        ));
    }

    // ==================== PayPal Refund Tests ====================

    @Test
    void testInitiateRefund_PayPal_Success() {
        // Given
        successfulTransaction.setPaymentChannel("PAYPAL");
        successfulTransaction.setProviderTransactionId("CAPTURE-123");

        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());
        request.setAmount(new BigDecimal("50.00"));
        request.setCurrency("USD");
        request.setReason("Customer request");

        when(paymentTransactionRepository.findById(transactionId))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("PAYPAL")).thenReturn(payPalProvider);

        PayPalRefundResponse paypalResponse = PayPalRefundResponse.builder()
                .id("REFUND-123")
                .status("COMPLETED")
                .build();
        when(payPalProvider.processRefund(eq("CAPTURE-123"), eq(new BigDecimal("50.00")), eq("USD"), eq("Customer request")))
                .thenReturn(paypalResponse);

        // When
        RefundResponse response = paymentService.initiateRefund(request);

        // Then
        assertNotNull(response);
        assertEquals(RefundStatus.COMPLETED, response.getStatus());
        assertEquals("REFUND-123", response.getProviderRefundId());
    }

    @Test
    void testInitiateRefund_PayPal_WithCaptureId() {
        // Given
        successfulTransaction.setPaymentChannel("PAYPAL");

        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());
        request.setCaptureId("CUSTOM-CAPTURE-ID");
        request.setAmount(new BigDecimal("30.00"));
        request.setCurrency("USD");

        when(paymentTransactionRepository.findById(transactionId))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("PAYPAL")).thenReturn(payPalProvider);

        PayPalRefundResponse paypalResponse = PayPalRefundResponse.builder()
                .id("REFUND-456")
                .status("COMPLETED")
                .build();
        when(payPalProvider.processRefund(eq("CUSTOM-CAPTURE-ID"), any(), any(), any()))
                .thenReturn(paypalResponse);

        // When
        RefundResponse response = paymentService.initiateRefund(request);

        // Then
        assertNotNull(response);
        assertEquals(RefundStatus.COMPLETED, response.getStatus());
        verify(payPalProvider).processRefund(eq("CUSTOM-CAPTURE-ID"), any(), any(), any());
    }

    // ==================== Error Cases ====================

    @Test
    void testInitiateRefund_InvalidTransactionId() {
        // Given
        RefundRequest request = new RefundRequest();
        request.setTransactionId("invalid-uuid");

        // When
        RefundResponse response = paymentService.initiateRefund(request);

        // Then
        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertEquals("Invalid transaction ID format", response.getErrorMessage());
    }

    @Test
    void testInitiateRefund_TransactionNotFound() {
        // Given
        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());

        when(paymentTransactionRepository.findById(transactionId))
                .thenReturn(Optional.empty());

        // When
        RefundResponse response = paymentService.initiateRefund(request);

        // Then
        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertEquals("Transaction not found", response.getErrorMessage());
    }

    @Test
    void testInitiateRefund_TransactionNotRefundable() {
        // Given
        successfulTransaction.setStatus(PaymentStatus.PROCESSING.name());

        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());

        when(paymentTransactionRepository.findById(transactionId))
                .thenReturn(Optional.of(successfulTransaction));

        // When
        RefundResponse response = paymentService.initiateRefund(request);

        // Then
        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertEquals("Transaction is not in a refundable state", response.getErrorMessage());
    }

    @Test
    void testInitiateRefund_NoProviderTransactionId() {
        // Given
        successfulTransaction.setProviderTransactionId(null);

        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());

        when(paymentTransactionRepository.findById(transactionId))
                .thenReturn(Optional.of(successfulTransaction));

        // When
        RefundResponse response = paymentService.initiateRefund(request);

        // Then
        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertEquals("No provider transaction ID found", response.getErrorMessage());
    }

    @Test
    void testInitiateRefund_UnsupportedPaymentChannel() {
        // Given
        successfulTransaction.setPaymentChannel("UNKNOWN_PROVIDER");

        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());

        when(paymentTransactionRepository.findById(transactionId))
                .thenReturn(Optional.of(successfulTransaction));

        // When
        RefundResponse response = paymentService.initiateRefund(request);

        // Then
        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertTrue(response.getErrorMessage().contains("Unsupported payment channel"));
    }

    @Test
    void testInitiateRefund_ProviderException() {
        // Given
        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());
        request.setAmount(new BigDecimal("50.00"));

        when(paymentTransactionRepository.findById(transactionId))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(stripeProvider);
        when(stripeProvider.processRefund(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Provider error"));

        // When
        RefundResponse response = paymentService.initiateRefund(request);

        // Then
        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertTrue(response.getErrorMessage().contains("Refund processing failed"));
    }

    // ==================== PayPal Status Mapping Tests ====================

    @Test
    void testMapPayPalRefundStatus_Completed() {
        // Use reflection to test private method
        RefundStatus result = org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(paymentService, "mapPayPalRefundStatus", "COMPLETED");
        assertEquals(RefundStatus.COMPLETED, result);
    }

    @Test
    void testMapPayPalRefundStatus_Pending() {
        RefundStatus result = org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(paymentService, "mapPayPalRefundStatus", "PENDING");
        assertEquals(RefundStatus.PENDING, result);
    }

    @Test
    void testMapPayPalRefundStatus_Failed() {
        RefundStatus result = org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(paymentService, "mapPayPalRefundStatus", "FAILED");
        assertEquals(RefundStatus.FAILED, result);
    }

    @Test
    void testMapPayPalRefundStatus_Cancelled() {
        RefundStatus result = org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(paymentService, "mapPayPalRefundStatus", "CANCELLED");
        assertEquals(RefundStatus.CANCELLED, result);
    }

    @Test
    void testMapPayPalRefundStatus_Unknown() {
        RefundStatus result = org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(paymentService, "mapPayPalRefundStatus", "UNKNOWN");
        assertEquals(RefundStatus.PROCESSING, result);
    }

    @Test
    void testMapPayPalRefundStatus_Null() {
        RefundStatus result = org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(paymentService, "mapPayPalRefundStatus", (String) null);
        assertEquals(RefundStatus.PENDING, result);
    }
}
