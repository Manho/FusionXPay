package com.fusionxpay.order.event;

import com.fusionxpay.common.event.OrderPaymentEvent;
import com.fusionxpay.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderService orderService;
    
    @KafkaListener(topics = "${kafka.topics.payment-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumePaymentEvent(OrderPaymentEvent event) {
        log.info("Received payment event for order: {}, status: {}", event.getOrderId(), event.getStatus());
        
        try {
            switch (event.getStatus()) {
                case INITIATED:
                case PROCESSING:
                    orderService.updateOrderStatusById(event.getOrderId(), OrderService.PROCESSING, event.getMessage());
                    break;
                case SUCCESS:
                    orderService.updateOrderStatusById(event.getOrderId(), OrderService.SUCCESS, event.getMessage());
                    break;
                case FAILED:
                    orderService.updateOrderStatusById(event.getOrderId(), OrderService.FAILED, event.getMessage());
                    break;
                default:
                    log.warn("Unknown payment status received: {}", event.getStatus());
            }
        } catch (Exception e) {
            log.error("Error processing payment event for order: {}", event.getOrderId(), e);
        }
    }
}