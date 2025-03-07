package com.fusionxpay.payment.event;

import com.fusionxpay.common.event.OrderPaymentEvent;
import com.fusionxpay.common.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderPaymentEvent> kafkaTemplate;
    
    @Value("${kafka.topics.payment-events}")
    private String paymentEventsTopic;

    /**
     * Send payment status update event to message queue
     *
     * @param orderId Order ID
     * @param transactionId Payment transaction ID
     * @param status Payment status
     */
    public void sendPaymentStatusUpdate(UUID orderId, UUID transactionId, com.fusionxpay.common.model.PaymentStatus status) {
        log.info("Sending payment status update for order: {}, status: {}", orderId, status);
        
        OrderPaymentEvent event = OrderPaymentEvent.builder()
                .orderId(orderId)
                .transactionId(transactionId)
                .status(status)
                .timestamp(LocalDateTime.now())
                .message(generateStatusMessage(status))
                .build();
        
        // Using order ID as the message key to ensure messages for the same order are sent to the same partition, guaranteeing order
        kafkaTemplate.send(paymentEventsTopic, orderId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Successfully sent payment status update for order: {}", orderId);
                    } else {
                        log.error("Failed to send payment status update for order: {}", orderId, ex);
                    }
                });
    }
    
    private String generateStatusMessage(PaymentStatus status) {
        switch (status) {
            case INITIATED:
                return "Payment initiated";
            case PROCESSING:
                return "Payment is being processed";
            case SUCCESS:
                return "Payment completed successfully";
            case FAILED:
                return "Payment failed";
            default:
                return "Payment status updated to " + status;
        }
    }
}