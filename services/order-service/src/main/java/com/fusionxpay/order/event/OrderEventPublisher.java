package com.fusionxpay.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventPublisher {
    
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private static final String TOPIC = "order-events";

    public void publishOrderEvent(OrderEvent event) {
        log.info("Publishing order event: {}", event);
        try {
            kafkaTemplate.send(TOPIC, event.getOrderNumber(), event);
            log.info("Event published successfully");
        } catch (Exception e) {
            log.error("Failed to publish event: {}", e.getMessage(), e);
            // TODO: Handle event publishing failure
            // 1. Retry mechanism
            // 2. Store failed events in a database for later retry
            // 3. Trigger alerts
        }
    }
}