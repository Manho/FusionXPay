package com.fusionxpay.order.event;

import com.fusionxpay.common.event.OrderPaymentEvent;
import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentEventConsumer paymentEventConsumer;

    private UUID orderId;
    private OrderPaymentEvent paymentEvent;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Test consuming INITIATED payment event")
    void consumeInitiatedPaymentEvent() {
        // Create a payment event with INITIATED status
        paymentEvent = new OrderPaymentEvent();
        paymentEvent.setOrderId(orderId);
        paymentEvent.setStatus(PaymentStatus.INITIATED);
        paymentEvent.setMessage("Payment initiated");

        // Consume the event
        paymentEventConsumer.consumePaymentEvent(paymentEvent);

        // Verify that the order service was called to update the order status
        verify(orderService).updateOrderStatusById(orderId, OrderService.PROCESSING, "Payment initiated");
        verifyNoMoreInteractions(orderService);
    }

    @Test
    @DisplayName("Test consuming PROCESSING payment event")
    void consumeProcessingPaymentEvent() {
        // Create a payment event with PROCESSING status
        paymentEvent = new OrderPaymentEvent();
        paymentEvent.setOrderId(orderId);
        paymentEvent.setStatus(PaymentStatus.PROCESSING);
        paymentEvent.setMessage("Payment processing");

        // Consume the event
        paymentEventConsumer.consumePaymentEvent(paymentEvent);

        // Verify that the order service was called to update the order status
        verify(orderService).updateOrderStatusById(orderId, OrderService.PROCESSING, "Payment processing");
        verifyNoMoreInteractions(orderService);
    }

    @Test
    @DisplayName("Test consuming SUCCESS payment event")
    void consumeSuccessPaymentEvent() {
        // Create a payment event with SUCCESS status
        paymentEvent = new OrderPaymentEvent();
        paymentEvent.setOrderId(orderId);
        paymentEvent.setStatus(PaymentStatus.SUCCESS);
        paymentEvent.setMessage("Payment successful");

        // Consume the event
        paymentEventConsumer.consumePaymentEvent(paymentEvent);

        // Verify that the order service was called to update the order status
        verify(orderService).updateOrderStatusById(orderId, OrderService.SUCCESS, "Payment successful");
        verifyNoMoreInteractions(orderService);
    }

    @Test
    @DisplayName("Test consuming FAILED payment event")
    void consumeFailedPaymentEvent() {
        // Create a payment event with FAILED status
        paymentEvent = new OrderPaymentEvent();
        paymentEvent.setOrderId(orderId);
        paymentEvent.setStatus(PaymentStatus.FAILED);
        paymentEvent.setMessage("Payment failed");

        // Consume the event
        paymentEventConsumer.consumePaymentEvent(paymentEvent);

        // Verify that the order service was called to update the order status
        verify(orderService).updateOrderStatusById(orderId, OrderService.FAILED, "Payment failed");
        verifyNoMoreInteractions(orderService);
    }

    @Test
    @DisplayName("Test consuming REFUNDED payment event")
    void consumeRefundedPaymentEvent() {
        paymentEvent = new OrderPaymentEvent();
        paymentEvent.setOrderId(orderId);
        paymentEvent.setStatus(PaymentStatus.REFUNDED);
        paymentEvent.setMessage("Refund completed");

        paymentEventConsumer.consumePaymentEvent(paymentEvent);

        verify(orderService).updateOrderStatusById(orderId, OrderService.REFUNDED, "Refund completed");
        verifyNoMoreInteractions(orderService);
    }

    @Test
    @DisplayName("Null event should be skipped")
    void consumeNullEvent() {
        paymentEventConsumer.consumePaymentEvent(null);
        verifyNoInteractions(orderService);
    }
}
