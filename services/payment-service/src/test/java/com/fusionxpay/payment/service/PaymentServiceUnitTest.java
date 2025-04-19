package com.fusionxpay.payment.service;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.event.OrderEventProducer;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.provider.PaymentProvider;
import com.fusionxpay.payment.provider.PaymentProviderFactory;
import com.fusionxpay.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceUnitTest {

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

    private PaymentRequest paymentRequest;
    private PaymentTransaction paymentTransaction;
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

        // Setup payment transaction
        paymentTransaction = new PaymentTransaction();
        paymentTransaction.setTransactionId(transactionId);
        paymentTransaction.setOrderId(orderId);
        paymentTransaction.setAmount(new BigDecimal("100.00"));
        paymentTransaction.setCurrency("USD");
        paymentTransaction.setPaymentChannel("STRIPE");
        paymentTransaction.setStatus(PaymentStatus.INITIATED.name());
    }

    @Test
    void testGetAvailablePaymentProviders() {
        // When
        List<String> providers = paymentService.getAvailablePaymentProviders();
        
        // Then
        assertNotNull(providers);
        assertEquals(2, providers.size());
        assertTrue(providers.contains("STRIPE"));
        assertTrue(providers.contains("PAYPAL"));
    }

    @Test
    void testGetPaymentTransactionByOrderId_NotFound() {
        // Given
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
        paymentTransaction.setStatus(PaymentStatus.SUCCESS.name());
        when(paymentTransactionRepository.findByOrderId(orderId)).thenReturn(Optional.of(paymentTransaction));
        
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
        when(paymentTransactionRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(paymentProvider);
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenReturn(paymentTransaction);
        
        PaymentResponse providerResponse = PaymentResponse.builder()
                .transactionId(transactionId)
                .orderId(orderId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentChannel("STRIPE")
                .status(PaymentStatus.PROCESSING)
                .redirectUrl("https://stripe.com/checkout/123")
                .build();
        
        when(paymentProvider.processPayment(paymentRequest)).thenReturn(providerResponse);

        // When
        PaymentResponse response = paymentService.initiatePayment(paymentRequest);

        // Then
        assertNotNull(response);
        assertEquals(transactionId, response.getTransactionId());
        assertEquals(orderId, response.getOrderId());
        
        verify(paymentTransactionRepository).findByOrderId(orderId);
        verify(paymentProviderFactory).getProvider("STRIPE");
        verify(paymentTransactionRepository, times(2)).save(any(PaymentTransaction.class));
        verify(paymentProvider).processPayment(paymentRequest);
        
        // Verify that the order event producer was called
        ArgumentCaptor<UUID> orderIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> transactionIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<PaymentStatus> statusCaptor = ArgumentCaptor.forClass(PaymentStatus.class);
        
        verify(orderEventProducer).sendPaymentStatusUpdate(
                orderIdCaptor.capture(), 
                transactionIdCaptor.capture(), 
                statusCaptor.capture());
        
        assertEquals(orderId, orderIdCaptor.getValue());
        assertEquals(transactionId, transactionIdCaptor.getValue());
    }
    
    @Test
    void testInitiatePayment_ExistingTransaction() {
        // Given
        paymentTransaction.setStatus(PaymentStatus.PROCESSING.name());
        when(paymentTransactionRepository.findByOrderId(orderId)).thenReturn(Optional.of(paymentTransaction));
        
        // When
        PaymentResponse response = paymentService.initiatePayment(paymentRequest);
        
        // Then
        assertNotNull(response);
        assertEquals(transactionId, response.getTransactionId());
        assertEquals(orderId, response.getOrderId());
        assertEquals(PaymentStatus.PROCESSING, response.getStatus());
        
        verify(paymentTransactionRepository).findByOrderId(orderId);
        verifyNoInteractions(paymentProviderFactory);
        verifyNoInteractions(paymentProvider);
        verifyNoMoreInteractions(paymentTransactionRepository);
    }
}
