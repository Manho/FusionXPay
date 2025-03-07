package com.fusionxpay.payment.event;

import com.fusionxpay.common.event.OrderPaymentEvent;
import com.fusionxpay.common.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventProducerTest {

    @Mock
    private KafkaTemplate<String, OrderPaymentEvent> kafkaTemplate;

    @Mock
    private CompletableFuture<SendResult<String, OrderPaymentEvent>> completableFuture;

    @Captor
    private ArgumentCaptor<OrderPaymentEvent> eventCaptor;

    @InjectMocks
    private OrderEventProducer orderEventProducer;

    @Test
    void shouldSendPaymentStatusUpdate() {
        // Given
        UUID orderId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        PaymentStatus status = PaymentStatus.SUCCESS;
        
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(completableFuture);
        
        // Mock the whenComplete to do nothing (we're not testing the callback logic)
        doAnswer(invocation -> {
            // Just return the original completableFuture - the callback won't be executed in the test
            return completableFuture;
        }).when(completableFuture).whenComplete(any(BiConsumer.class));
        
        // When
        orderEventProducer.sendPaymentStatusUpdate(orderId, transactionId, status);
        
        // Then
        verify(kafkaTemplate).send(eq("payment-events"), eq(orderId.toString()), eventCaptor.capture());
        OrderPaymentEvent capturedEvent = eventCaptor.getValue();
        
        assertEquals(orderId, capturedEvent.getOrderId());
        assertEquals(transactionId, capturedEvent.getTransactionId());
        assertEquals(status, capturedEvent.getStatus());
        assertEquals("Payment completed successfully", capturedEvent.getMessage());
    }
}