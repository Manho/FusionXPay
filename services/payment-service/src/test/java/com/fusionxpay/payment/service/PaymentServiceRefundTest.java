package com.fusionxpay.payment.service;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.dto.RefundRequest;
import com.fusionxpay.payment.dto.RefundResponse;
import com.fusionxpay.payment.event.OrderEventProducer;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.model.RefundStatus;
import com.fusionxpay.payment.provider.PaymentProvider;
import com.fusionxpay.payment.provider.PaymentProviderFactory;
import com.fusionxpay.payment.provider.ProviderRefundRequest;
import com.fusionxpay.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PaymentService refund functionality.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceRefundTest {

    private static final long MERCHANT_ID = 23L;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @Mock
    private OrderEventProducer orderEventProducer;

    @Mock
    private PaymentProvider paymentProvider;

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
        successfulTransaction.setMerchantId(MERCHANT_ID);
        successfulTransaction.setAmount(new BigDecimal("100.00"));
        successfulTransaction.setCurrency("USD");
        successfulTransaction.setPaymentChannel("STRIPE");
        successfulTransaction.setStatus(PaymentStatus.SUCCESS.name());
        successfulTransaction.setProviderTransactionId("pi_test_123");
    }

    @Test
    void testInitiateRefund_BuildsStripeProviderRefundRequest() {
        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());
        request.setAmount(new BigDecimal("50.00"));
        request.setReason("Customer request");

        when(paymentTransactionRepository.findByTransactionIdAndMerchantId(transactionId, MERCHANT_ID))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(paymentProvider);
        when(paymentProvider.processRefund(any(ProviderRefundRequest.class)))
                .thenReturn(RefundResponse.builder()
                        .refundId("internal-refund-id")
                        .providerRefundId("re_stripe_123")
                        .status(RefundStatus.COMPLETED)
                        .amount(new BigDecimal("50.00"))
                        .currency("USD")
                        .paymentChannel("STRIPE")
                        .build());

        RefundResponse response = paymentService.initiateRefund(MERCHANT_ID, request);

        assertNotNull(response);
        assertEquals(RefundStatus.COMPLETED, response.getStatus());
        assertEquals(transactionId.toString(), response.getTransactionId());

        ArgumentCaptor<ProviderRefundRequest> captor = ArgumentCaptor.forClass(ProviderRefundRequest.class);
        verify(paymentProvider).processRefund(captor.capture());
        assertEquals("pi_test_123", captor.getValue().getProviderTransactionId());
        assertEquals(new BigDecimal("50.00"), captor.getValue().getAmount());
        assertEquals("USD", captor.getValue().getCurrency());
        assertEquals("Customer request", captor.getValue().getReason());
    }

    @Test
    void testInitiateRefund_FullRefundUsesTransactionCurrency() {
        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());
        request.setReason("Full refund");

        when(paymentTransactionRepository.findByTransactionIdAndMerchantId(transactionId, MERCHANT_ID))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(paymentProvider);
        when(paymentProvider.processRefund(any(ProviderRefundRequest.class)))
                .thenReturn(RefundResponse.builder()
                        .refundId("internal-refund-id")
                        .status(RefundStatus.PENDING)
                        .amount(new BigDecimal("100.00"))
                        .currency("USD")
                        .paymentChannel("STRIPE")
                        .build());

        RefundResponse response = paymentService.initiateRefund(MERCHANT_ID, request);

        assertNotNull(response);
        assertEquals(RefundStatus.PENDING, response.getStatus());

        ArgumentCaptor<ProviderRefundRequest> captor = ArgumentCaptor.forClass(ProviderRefundRequest.class);
        verify(paymentProvider).processRefund(captor.capture());
        assertEquals("pi_test_123", captor.getValue().getProviderTransactionId());
        assertNull(captor.getValue().getAmount());
        assertEquals("USD", captor.getValue().getCurrency());
        assertEquals("Full refund", captor.getValue().getReason());
    }

    @Test
    void testInitiateRefund_PayPalCaptureIdOverride() {
        successfulTransaction.setPaymentChannel("PAYPAL");
        successfulTransaction.setProviderTransactionId("CAPTURE-123");

        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());
        request.setCaptureId("CUSTOM-CAPTURE-ID");
        request.setAmount(new BigDecimal("30.00"));
        request.setCurrency("USD");
        request.setReason("Customer request");

        when(paymentTransactionRepository.findByTransactionIdAndMerchantId(transactionId, MERCHANT_ID))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("PAYPAL")).thenReturn(paymentProvider);
        when(paymentProvider.processRefund(any(ProviderRefundRequest.class)))
                .thenReturn(RefundResponse.builder()
                        .refundId("internal-refund-id")
                        .providerRefundId("REFUND-456")
                        .status(RefundStatus.COMPLETED)
                        .amount(new BigDecimal("30.00"))
                        .currency("USD")
                        .paymentChannel("PAYPAL")
                        .build());

        RefundResponse response = paymentService.initiateRefund(MERCHANT_ID, request);

        assertNotNull(response);
        assertEquals(RefundStatus.COMPLETED, response.getStatus());

        ArgumentCaptor<ProviderRefundRequest> captor = ArgumentCaptor.forClass(ProviderRefundRequest.class);
        verify(paymentProvider).processRefund(captor.capture());
        assertEquals("CUSTOM-CAPTURE-ID", captor.getValue().getProviderTransactionId());
        assertEquals(new BigDecimal("30.00"), captor.getValue().getAmount());
        assertEquals("USD", captor.getValue().getCurrency());
    }

    @Test
    void testInitiateRefund_PayPalCurrencyFallsBackToTransactionCurrency() {
        successfulTransaction.setPaymentChannel("PAYPAL");
        successfulTransaction.setProviderTransactionId("CAPTURE-123");

        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());
        request.setAmount(new BigDecimal("30.00"));

        when(paymentTransactionRepository.findByTransactionIdAndMerchantId(transactionId, MERCHANT_ID))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("PAYPAL")).thenReturn(paymentProvider);
        when(paymentProvider.processRefund(any(ProviderRefundRequest.class)))
                .thenReturn(RefundResponse.builder()
                        .refundId("internal-refund-id")
                        .providerRefundId("REFUND-456")
                        .status(RefundStatus.COMPLETED)
                        .amount(new BigDecimal("30.00"))
                        .currency("USD")
                        .paymentChannel("PAYPAL")
                        .build());

        paymentService.initiateRefund(MERCHANT_ID, request);

        ArgumentCaptor<ProviderRefundRequest> captor = ArgumentCaptor.forClass(ProviderRefundRequest.class);
        verify(paymentProvider).processRefund(captor.capture());
        assertEquals("CAPTURE-123", captor.getValue().getProviderTransactionId());
        assertEquals("USD", captor.getValue().getCurrency());
    }

    @Test
    void testInitiateRefund_ProviderReturnsFailedResponse() {
        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());
        request.setAmount(new BigDecimal("50.00"));

        when(paymentTransactionRepository.findByTransactionIdAndMerchantId(transactionId, MERCHANT_ID))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(paymentProvider);
        when(paymentProvider.processRefund(any(ProviderRefundRequest.class)))
                .thenReturn(RefundResponse.builder()
                        .refundId("internal-refund-id")
                        .status(RefundStatus.FAILED)
                        .errorMessage("Insufficient funds")
                        .paymentChannel("STRIPE")
                        .build());

        RefundResponse response = paymentService.initiateRefund(MERCHANT_ID, request);

        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertEquals("Insufficient funds", response.getErrorMessage());
    }

    @Test
    void testInitiateRefund_InvalidTransactionId() {
        RefundRequest request = new RefundRequest();
        request.setTransactionId("invalid-uuid");

        RefundResponse response = paymentService.initiateRefund(MERCHANT_ID, request);

        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertEquals("Invalid transaction ID format", response.getErrorMessage());
        verifyNoInteractions(paymentProviderFactory);
    }

    @Test
    void testInitiateRefund_TransactionNotFound() {
        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());

        when(paymentTransactionRepository.findByTransactionIdAndMerchantId(transactionId, MERCHANT_ID))
                .thenReturn(Optional.empty());

        RefundResponse response = paymentService.initiateRefund(MERCHANT_ID, request);

        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertEquals("Transaction not found", response.getErrorMessage());
    }

    @Test
    void testInitiateRefund_TransactionNotRefundable() {
        successfulTransaction.setStatus(PaymentStatus.PROCESSING.name());

        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());

        when(paymentTransactionRepository.findByTransactionIdAndMerchantId(transactionId, MERCHANT_ID))
                .thenReturn(Optional.of(successfulTransaction));

        RefundResponse response = paymentService.initiateRefund(MERCHANT_ID, request);

        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertEquals("Transaction is not in a refundable state", response.getErrorMessage());
    }

    @Test
    void testInitiateRefund_NoProviderTransactionId() {
        successfulTransaction.setProviderTransactionId(null);

        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());

        when(paymentTransactionRepository.findByTransactionIdAndMerchantId(transactionId, MERCHANT_ID))
                .thenReturn(Optional.of(successfulTransaction));

        RefundResponse response = paymentService.initiateRefund(MERCHANT_ID, request);

        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertEquals("No provider transaction ID found", response.getErrorMessage());
    }

    @Test
    void testInitiateRefund_UnsupportedPaymentChannel() {
        successfulTransaction.setPaymentChannel("UNKNOWN_PROVIDER");

        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());

        when(paymentTransactionRepository.findByTransactionIdAndMerchantId(transactionId, MERCHANT_ID))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("UNKNOWN_PROVIDER"))
                .thenThrow(new IllegalArgumentException("Unsupported payment provider: UNKNOWN_PROVIDER"));

        RefundResponse response = paymentService.initiateRefund(MERCHANT_ID, request);

        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertEquals("Unsupported payment channel: UNKNOWN_PROVIDER", response.getErrorMessage());
    }

    @Test
    void testInitiateRefund_UnexpectedProviderException() {
        RefundRequest request = new RefundRequest();
        request.setTransactionId(transactionId.toString());
        request.setAmount(new BigDecimal("50.00"));

        when(paymentTransactionRepository.findByTransactionIdAndMerchantId(transactionId, MERCHANT_ID))
                .thenReturn(Optional.of(successfulTransaction));
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(paymentProvider);
        when(paymentProvider.processRefund(any(ProviderRefundRequest.class)))
                .thenThrow(new RuntimeException("Provider error"));

        RefundResponse response = paymentService.initiateRefund(MERCHANT_ID, request);

        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertTrue(response.getErrorMessage().contains("Refund processing failed"));
        verify(paymentTransactionRepository, never()).save(org.mockito.ArgumentMatchers.any(PaymentTransaction.class));
    }
}
