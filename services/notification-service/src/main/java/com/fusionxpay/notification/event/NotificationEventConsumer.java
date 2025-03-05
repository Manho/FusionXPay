package com.fusionxpay.notification.event;

import com.fusionxpay.notification.model.NotificationMessage;
import com.fusionxpay.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    
    // Final statuses that trigger notifications
    private static final Set<String> NOTIFICATION_STATUSES = new HashSet<>(Arrays.asList("SUCCESS", "FAILED"));
    
    private static final Map<String, String> EVENT_TYPE_MAPPING = Map.of(
        "PAYMENT_SUCCESS", "PAYMENT_CONFIRMATION",
        "PAYMENT_FAILED", "PAYMENT_FAILURE"
    );

    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void consume(OrderEvent orderEvent) {
        log.info("Received order event: type={}, status={}, orderId={}", 
                orderEvent.getEventType(), orderEvent.getStatus(), orderEvent.getOrderId());

        // Only send notifications for SUCCESS or FAILED statuses
        if (!NOTIFICATION_STATUSES.contains(orderEvent.getStatus())) {
            log.info("Skipping notification for non-final status: {}", orderEvent.getStatus());
            return;
        }

        try {
            NotificationMessage message = createNotificationMessage(orderEvent);
            notificationService.createNotification(message);
            log.info("Notification created successfully for orderId={}, status={}", 
                    orderEvent.getOrderId(), orderEvent.getStatus());
        } catch (Exception ex) {
            log.error("Failed to process order event: orderId={}, status={}, error={}", 
                    orderEvent.getOrderId(), orderEvent.getStatus(), ex.getMessage(), ex);
        }
    }

    private NotificationMessage createNotificationMessage(OrderEvent orderEvent) {
        String content = generateContentFromEvent(orderEvent);
        String notificationType = mapEventToNotificationType(orderEvent);
        String recipient = String.format("user-%d@fusionxpay.com", orderEvent.getUserId());
        
        return NotificationMessage.builder()
                .orderId(orderEvent.getOrderId().toString())
                .eventType(notificationType)
                .content(content)
                .recipient(recipient)
                .build();
    }
    
    private String mapEventToNotificationType(OrderEvent orderEvent) {
        if ("SUCCESS".equals(orderEvent.getStatus())) {
            return "PAYMENT_CONFIRMATION";
        } else {
            return "PAYMENT_FAILURE";
        }
    }
    
    private String generateContentFromEvent(OrderEvent orderEvent) {
        if ("SUCCESS".equals(orderEvent.getStatus())) {
            return String.format("Payment of %s %s was successfully processed for order %s.",
                    orderEvent.getAmount(), orderEvent.getCurrency(), orderEvent.getOrderId());
        } else {
            return String.format("Payment of %s %s failed for order %s. Please check your payment method.",
                    orderEvent.getAmount(), orderEvent.getCurrency(), orderEvent.getOrderId());
        }
    }
}