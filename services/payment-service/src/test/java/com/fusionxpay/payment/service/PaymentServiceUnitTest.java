package com.fusionxpay.payment.service;

import com.fusionxpay.common.model.PaymentStatus;
import feign.FeignException;
import feign.Request;
import com.fusionxpay.payment.client.OrderServiceClient;
import com.fusionxpay.payment.dto.OrderResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceUnitTest {

    private static final long MERCHANT_ID = 42L;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @Mock
    private OrderEventProducer orderEventProducer;

    @Mock
    private OrderServiceClient orderServiceClient;

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
        paymentTransaction.setMerchantId(MERCHANT_ID);
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
        when(paymentTransactionRepository.findByOrderIdAndMerchantId(orderId, MERCHANT_ID)).thenReturn(Optional.empty());
        
        // When
        PaymentResponse response = paymentService.getPaymentTransactionByOrderId(MERCHANT_ID, orderId);
        
        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.NOT_FOUND, response.getStatus());
        assertEquals(orderId, response.getOrderId());
        verify(paymentTransactionRepository).findByOrderIdAndMerchantId(orderId, MERCHANT_ID);
    }
    
    @Test
    void testGetPaymentTransactionByOrderId_Found() {
        // Given
        paymentTransaction.setStatus(PaymentStatus.SUCCESS.name());
        when(paymentTransactionRepository.findByOrderIdAndMerchantId(orderId, MERCHANT_ID)).thenReturn(Optional.of(paymentTransaction));
        
        // When
        PaymentResponse response = paymentService.getPaymentTransactionByOrderId(MERCHANT_ID, orderId);
        
        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals(orderId, response.getOrderId());
        assertEquals(transactionId, response.getTransactionId());
        verify(paymentTransactionRepository).findByOrderIdAndMerchantId(orderId, MERCHANT_ID);
    }
    
    @Test
    void testInitiatePayment_NewTransaction() {
        // Given
        when(paymentTransactionRepository.findByOrderIdAndMerchantId(orderId, MERCHANT_ID)).thenReturn(Optional.empty());
        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(paymentProvider);
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenReturn(paymentTransaction);
        when(orderServiceClient.getOrderById(MERCHANT_ID, orderId)).thenReturn(ResponseEntity.ok(
                OrderResponse.builder().orderId(orderId).userId(MERCHANT_ID).build()
        ));
        
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
        PaymentResponse response = paymentService.initiatePayment(MERCHANT_ID, paymentRequest);

        // Then
        assertNotNull(response);
        assertEquals(transactionId, response.getTransactionId());
        assertEquals(orderId, response.getOrderId());
        
        verify(paymentTransactionRepository).findByOrderIdAndMerchantId(orderId, MERCHANT_ID);
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
        when(paymentTransactionRepository.findByOrderIdAndMerchantId(orderId, MERCHANT_ID)).thenReturn(Optional.of(paymentTransaction));
        when(orderServiceClient.getOrderById(MERCHANT_ID, orderId)).thenReturn(ResponseEntity.ok(
                OrderResponse.builder().orderId(orderId).userId(MERCHANT_ID).build()
        ));
        
        // When
        PaymentResponse response = paymentService.initiatePayment(MERCHANT_ID, paymentRequest);
        
        // Then
        assertNotNull(response);
        assertEquals(transactionId, response.getTransactionId());
        assertEquals(orderId, response.getOrderId());
        assertEquals(PaymentStatus.PROCESSING, response.getStatus());
        
        verify(paymentTransactionRepository).findByOrderIdAndMerchantId(orderId, MERCHANT_ID);
        verifyNoInteractions(paymentProviderFactory);
        verifyNoInteractions(paymentProvider);
        verifyNoMoreInteractions(paymentTransactionRepository);
    }

    @Test
    void testInitiatePayment_ThrowsForbiddenWhenOrderBelongsToAnotherMerchant() {
        when(orderServiceClient.getOrderById(MERCHANT_ID, orderId)).thenThrow(feignForbidden());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> paymentService.initiatePayment(MERCHANT_ID, paymentRequest));

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(paymentProviderFactory);
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void testInitiatePayment_ThrowsBadRequestWhenOrderDoesNotExist() {
        when(orderServiceClient.getOrderById(MERCHANT_ID, orderId)).thenThrow(feignNotFound());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> paymentService.initiatePayment(MERCHANT_ID, paymentRequest));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(paymentProviderFactory);
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void testHandleCallback_UpdatesProviderTransactionId() {
        // Given
        PaymentTransaction existing = new PaymentTransaction();
        existing.setTransactionId(UUID.randomUUID());
        existing.setOrderId(orderId);
        existing.setMerchantId(MERCHANT_ID);
        existing.setAmount(new BigDecimal("100.00"));
        existing.setCurrency("USD");
        existing.setPaymentChannel("STRIPE");
        existing.setStatus(PaymentStatus.PROCESSING.name());
        existing.setProviderTransactionId("cs_test_initial");

        when(paymentProviderFactory.getProvider("STRIPE")).thenReturn(paymentProvider);
        when(paymentProvider.validateCallback(anyString(), anyString())).thenReturn(true);
        when(paymentProvider.processCallback(anyString(), anyString())).thenReturn(PaymentResponse.builder()
                .orderId(orderId)
                .status(PaymentStatus.SUCCESS)
                .paymentChannel("STRIPE")
                .providerTransactionId("pi_test_final")
                .build());

        when(paymentTransactionRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        // When
        boolean processed = paymentService.handleCallback("{}", "sig", "STRIPE");

        // Then
        assertTrue(processed);
        verify(paymentTransactionRepository).save(argThat(tx ->
                "pi_test_final".equals(tx.getProviderTransactionId())
        ));
    }

    private FeignException.Forbidden feignForbidden() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/orders/id/" + orderId, Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.Forbidden("Forbidden", request, null, Map.of());
    }

    private FeignException.NotFound feignNotFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/orders/id/" + orderId, Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.NotFound("Not Found", request, null, Map.of());
    }
}
