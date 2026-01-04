package com.fusionxpay.notification.event;

import com.fusionxpay.notification.model.NotificationMessage;
import com.fusionxpay.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventConsumer notificationEventConsumer;

    @Test
    @DisplayName("SUCCESS event creates payment confirmation notification")
    void consumeSuccessEvent() {
        OrderEvent event = OrderEvent.builder()
                .orderId(UUID.randomUUID())
                .status("SUCCESS")
                .userId(10L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        notificationEventConsumer.consume(event);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService).createNotification(captor.capture());

        NotificationMessage message = captor.getValue();
        assertEquals("PAYMENT_CONFIRMATION", message.getEventType());
    }

    @Test
    @DisplayName("FAILED event creates payment failure notification")
    void consumeFailedEvent() {
        OrderEvent event = OrderEvent.builder()
                .orderId(UUID.randomUUID())
                .status("FAILED")
                .userId(10L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .build();

        notificationEventConsumer.consume(event);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationService).createNotification(captor.capture());

        NotificationMessage message = captor.getValue();
        assertEquals("PAYMENT_FAILURE", message.getEventType());
    }

    @Test
    @DisplayName("Non-final status does not create notification")
    void consumeNonFinalEvent() {
        OrderEvent event = OrderEvent.builder()
                .orderId(UUID.randomUUID())
                .status("PROCESSING")
                .userId(10L)
                .amount(new BigDecimal("20.00"))
                .currency("USD")
                .build();

        notificationEventConsumer.consume(event);

        verifyNoInteractions(notificationService);
    }
}
