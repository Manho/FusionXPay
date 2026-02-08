package com.fusionxpay.order.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fusionxpay.common.event.OrderPaymentEvent;
import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import com.fusionxpay.order.model.Order;
import com.fusionxpay.order.repository.OrderRepository;
import com.fusionxpay.order.service.OrderService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for Kafka message flow in order-service.
 * Tests consumer behavior for payment events and idempotency.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class KafkaMessageFlowIT extends AbstractIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Value("${kafka.topics.payment-events:payment-events}")
    private String paymentEventsTopic;

    private KafkaTemplate<String, OrderPaymentEvent> kafkaTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Create Kafka producer for tests
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        DefaultKafkaProducerFactory<String, OrderPaymentEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    @Test
    @DisplayName("Kafka consumer processes payment SUCCESS event and updates order status")
    void testKafkaConsumer_ProcessesPaymentSuccessEvent() {
        // 1. Create test order in NEW status
        Order testOrder = createTestOrder(OrderService.NEW);

        // 2. Transition to PROCESSING first (required by state machine)
        testOrder.setStatus(OrderService.PROCESSING);
        orderRepository.save(testOrder);

        // 3. Send Kafka payment SUCCESS event
        OrderPaymentEvent paymentEvent = OrderPaymentEvent.builder()
                .orderId(testOrder.getOrderId())
                .transactionId(UUID.randomUUID())
                .status(PaymentStatus.SUCCESS)
                .amount(testOrder.getAmount())
                .currency(testOrder.getCurrency())
                .paymentChannel("STRIPE")
                .message("Payment completed successfully")
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send(paymentEventsTopic, testOrder.getOrderId().toString(), paymentEvent);

        // 4. Wait and verify order status updated to SUCCESS
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> orderRepository.findById(testOrder.getOrderId())
                        .map(o -> OrderService.SUCCESS.equals(o.getStatus()))
                        .orElse(false));

        Order updatedOrder = orderRepository.findById(testOrder.getOrderId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderService.SUCCESS);
    }

    @Test
    @DisplayName("Kafka consumer processes payment FAILED event and updates order status")
    void testKafkaConsumer_ProcessesPaymentFailedEvent() {
        // 1. Create test order in PROCESSING status
        Order testOrder = createTestOrder(OrderService.PROCESSING);

        // 2. Send Kafka payment FAILED event
        OrderPaymentEvent paymentEvent = OrderPaymentEvent.builder()
                .orderId(testOrder.getOrderId())
                .transactionId(UUID.randomUUID())
                .status(PaymentStatus.FAILED)
                .amount(testOrder.getAmount())
                .currency(testOrder.getCurrency())
                .paymentChannel("STRIPE")
                .message("Insufficient funds")
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send(paymentEventsTopic, testOrder.getOrderId().toString(), paymentEvent);

        // 3. Wait and verify order status updated to FAILED
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> orderRepository.findById(testOrder.getOrderId())
                        .map(o -> OrderService.FAILED.equals(o.getStatus()))
                        .orElse(false));

        Order updatedOrder = orderRepository.findById(testOrder.getOrderId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderService.FAILED);
    }

    @Test
    @DisplayName("Kafka consumer processes PROCESSING event and updates order from NEW to PROCESSING")
    void testKafkaConsumer_ProcessesProcessingEvent() {
        // 1. Create test order in NEW status
        Order testOrder = createTestOrder(OrderService.NEW);

        // 2. Send Kafka payment PROCESSING event
        OrderPaymentEvent paymentEvent = OrderPaymentEvent.builder()
                .orderId(testOrder.getOrderId())
                .transactionId(UUID.randomUUID())
                .status(PaymentStatus.PROCESSING)
                .amount(testOrder.getAmount())
                .currency(testOrder.getCurrency())
                .paymentChannel("STRIPE")
                .message("Payment is being processed")
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send(paymentEventsTopic, testOrder.getOrderId().toString(), paymentEvent);

        // 3. Wait and verify order status updated to PROCESSING
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> orderRepository.findById(testOrder.getOrderId())
                        .map(o -> OrderService.PROCESSING.equals(o.getStatus()))
                        .orElse(false));

        Order updatedOrder = orderRepository.findById(testOrder.getOrderId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderService.PROCESSING);
    }

    @Test
    @DisplayName("Consumer idempotency: Duplicate messages should not cause issues")
    void testConsumerIdempotency_DuplicateMessagesHandledGracefully() {
        // 1. Create test order in PROCESSING status
        Order testOrder = createTestOrder(OrderService.PROCESSING);

        // 2. Send the same payment SUCCESS event multiple times
        OrderPaymentEvent paymentEvent = OrderPaymentEvent.builder()
                .orderId(testOrder.getOrderId())
                .transactionId(UUID.randomUUID())
                .status(PaymentStatus.SUCCESS)
                .amount(testOrder.getAmount())
                .currency(testOrder.getCurrency())
                .paymentChannel("STRIPE")
                .message("Payment completed successfully")
                .timestamp(LocalDateTime.now())
                .build();

        // Send duplicate messages
        kafkaTemplate.send(paymentEventsTopic, testOrder.getOrderId().toString(), paymentEvent);
        kafkaTemplate.send(paymentEventsTopic, testOrder.getOrderId().toString(), paymentEvent);
        kafkaTemplate.send(paymentEventsTopic, testOrder.getOrderId().toString(), paymentEvent);

        // 3. Wait for processing
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> orderRepository.findById(testOrder.getOrderId())
                        .map(o -> OrderService.SUCCESS.equals(o.getStatus()))
                        .orElse(false));

        // 4. Verify order is in SUCCESS state (duplicates handled gracefully)
        Order updatedOrder = orderRepository.findById(testOrder.getOrderId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderService.SUCCESS);

        // Additional small wait to ensure all messages are processed
        await().during(2, TimeUnit.SECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> {
                    Order order = orderRepository.findById(testOrder.getOrderId()).orElseThrow();
                    // Order should still be SUCCESS after processing duplicate messages
                    return OrderService.SUCCESS.equals(order.getStatus());
                });
    }

    @Test
    @DisplayName("Consumer handles event for non-existent order gracefully")
    void testConsumer_HandlesNonExistentOrderGracefully() {
        // Send payment event for non-existent order
        UUID nonExistentOrderId = UUID.randomUUID();

        OrderPaymentEvent paymentEvent = OrderPaymentEvent.builder()
                .orderId(nonExistentOrderId)
                .transactionId(UUID.randomUUID())
                .status(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .paymentChannel("STRIPE")
                .message("Payment completed successfully")
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send(paymentEventsTopic, nonExistentOrderId.toString(), paymentEvent);

        // Wait a bit and verify no order was created
        await().during(3, TimeUnit.SECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> orderRepository.findById(nonExistentOrderId).isEmpty());
    }

    private Order createTestOrder(String status) {
        Order order = Order.builder()
                .orderNumber("ORD-TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .userId(1L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(status)
                .build();
        return orderRepository.save(order);
    }
}
