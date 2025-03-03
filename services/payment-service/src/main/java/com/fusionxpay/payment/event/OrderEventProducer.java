package com.fusionxpay.payment.event;

import com.fusionxpay.payment.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderPaymentEvent> kafkaTemplate;
    
    @Value("${kafka.topics.payment-events}")
    private String paymentEventsTopic;
    
    public void sendPaymentStatusUpdate(UUID orderId, UUID transactionId, PaymentStatus status) {
        OrderPaymentEvent event = new OrderPaymentEvent(
                orderId,
                transactionId,
                status.name(),
                System.currentTimeMillis()
        );
        
        log.info("Sending payment status update to Kafka: {}", event);
        kafkaTemplate.send(paymentEventsTopic, orderId.toString(), event);
    }
}
