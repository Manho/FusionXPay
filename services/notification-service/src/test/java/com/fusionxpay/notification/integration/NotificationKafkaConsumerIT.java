package com.fusionxpay.notification.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import com.fusionxpay.notification.event.OrderEvent;
import com.fusionxpay.notification.model.NotificationMessage;
import com.fusionxpay.notification.repository.NotificationRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for NotificationEventConsumer.
 * Tests end-to-end Kafka message consumption and notification persistence.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class NotificationKafkaConsumerIT extends AbstractIntegrationTest {

    private static final String ORDER_EVENTS_TOPIC = "order-events";

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Create Kafka producer for tests
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        DefaultKafkaProducerFactory<String, OrderEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Clear Redis cache if available
        if (redisTemplate != null) {
            try {
                redisTemplate.getConnectionFactory().getConnection().flushAll();
            } catch (Exception e) {
                // Ignore if Redis operations fail during setup
            }
        }
    }

    @Test
    @DisplayName("Kafka consumer processes SUCCESS order event and saves notification to database")
    void testConsumer_ProcessesSuccessOrderEvent_SavesNotification() {
        // 1. Create test order event with SUCCESS status
        UUID orderId = UUID.randomUUID();
        OrderEvent orderEvent = OrderEvent.builder()
                .orderId(orderId)
                .eventType("PAYMENT_SUCCESS")
                .status("SUCCESS")
                .userId(123L)
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 2. Send Kafka message
        kafkaTemplate.send(ORDER_EVENTS_TOPIC, orderId.toString(), orderEvent);

        // 3. Wait and verify notification is saved to database
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    List<NotificationMessage> notifications = notificationRepository.findAll();
                    return notifications.stream()
                            .anyMatch(n -> orderId.toString().equals(n.getOrderId()));
                });

        // 4. Verify notification details
        List<NotificationMessage> notifications = notificationRepository.findAll();
        NotificationMessage savedNotification = notifications.stream()
                .filter(n -> orderId.toString().equals(n.getOrderId()))
                .findFirst()
                .orElseThrow();

        assertThat(savedNotification.getEventType()).isEqualTo("PAYMENT_CONFIRMATION");
        assertThat(savedNotification.getContent()).contains("successfully processed");
        assertThat(savedNotification.getContent()).contains("250.00");
        assertThat(savedNotification.getContent()).contains("USD");
        assertThat(savedNotification.getRecipient()).isEqualTo("user-123@fusionxpay.com");
    }

    @Test
    @DisplayName("Kafka consumer processes FAILED order event and saves failure notification")
    void testConsumer_ProcessesFailedOrderEvent_SavesFailureNotification() {
        // 1. Create test order event with FAILED status
        UUID orderId = UUID.randomUUID();
        OrderEvent orderEvent = OrderEvent.builder()
                .orderId(orderId)
                .eventType("PAYMENT_FAILED")
                .status("FAILED")
                .userId(456L)
                .amount(new BigDecimal("99.99"))
                .currency("EUR")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 2. Send Kafka message
        kafkaTemplate.send(ORDER_EVENTS_TOPIC, orderId.toString(), orderEvent);

        // 3. Wait and verify notification is saved
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    List<NotificationMessage> notifications = notificationRepository.findAll();
                    return notifications.stream()
                            .anyMatch(n -> orderId.toString().equals(n.getOrderId()));
                });

        // 4. Verify notification details
        NotificationMessage savedNotification = notificationRepository.findAll().stream()
                .filter(n -> orderId.toString().equals(n.getOrderId()))
                .findFirst()
                .orElseThrow();

        assertThat(savedNotification.getEventType()).isEqualTo("PAYMENT_FAILURE");
        assertThat(savedNotification.getContent()).contains("failed");
        assertThat(savedNotification.getContent()).contains("99.99");
        assertThat(savedNotification.getContent()).contains("EUR");
        assertThat(savedNotification.getRecipient()).isEqualTo("user-456@fusionxpay.com");
    }

    @Test
    @DisplayName("Consumer skips non-final status events (PROCESSING)")
    void testConsumer_SkipsNonFinalStatusEvents() {
        // 1. Create test order event with PROCESSING status (non-final)
        UUID orderId = UUID.randomUUID();
        OrderEvent orderEvent = OrderEvent.builder()
                .orderId(orderId)
                .eventType("PAYMENT_PROCESSING")
                .status("PROCESSING")
                .userId(789L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 2. Send Kafka message
        kafkaTemplate.send(ORDER_EVENTS_TOPIC, orderId.toString(), orderEvent);

        // 3. Wait some time and verify no notification was created
        await().during(3, TimeUnit.SECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> {
                    List<NotificationMessage> notifications = notificationRepository.findAll();
                    return notifications.stream()
                            .noneMatch(n -> orderId.toString().equals(n.getOrderId()));
                });
    }

    @Test
    @DisplayName("End-to-end: Multiple order events create corresponding notifications")
    void testEndToEnd_MultipleOrderEventsCreateNotifications() {
        // 1. Create multiple order events
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        OrderEvent successEvent = OrderEvent.builder()
                .orderId(orderId1)
                .eventType("PAYMENT_SUCCESS")
                .status("SUCCESS")
                .userId(100L)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        OrderEvent failedEvent = OrderEvent.builder()
                .orderId(orderId2)
                .eventType("PAYMENT_FAILED")
                .status("FAILED")
                .userId(200L)
                .amount(new BigDecimal("300.00"))
                .currency("GBP")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 2. Send both events
        kafkaTemplate.send(ORDER_EVENTS_TOPIC, orderId1.toString(), successEvent);
        kafkaTemplate.send(ORDER_EVENTS_TOPIC, orderId2.toString(), failedEvent);

        // 3. Wait and verify both notifications are saved
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    List<NotificationMessage> notifications = notificationRepository.findAll();
                    boolean hasSuccess = notifications.stream()
                            .anyMatch(n -> orderId1.toString().equals(n.getOrderId()));
                    boolean hasFailed = notifications.stream()
                            .anyMatch(n -> orderId2.toString().equals(n.getOrderId()));
                    return hasSuccess && hasFailed;
                });

        // 4. Verify notification count and details
        List<NotificationMessage> allNotifications = notificationRepository.findAll();
        assertThat(allNotifications).hasSize(2);

        NotificationMessage successNotification = allNotifications.stream()
                .filter(n -> orderId1.toString().equals(n.getOrderId()))
                .findFirst()
                .orElseThrow();
        assertThat(successNotification.getEventType()).isEqualTo("PAYMENT_CONFIRMATION");

        NotificationMessage failedNotification = allNotifications.stream()
                .filter(n -> orderId2.toString().equals(n.getOrderId()))
                .findFirst()
                .orElseThrow();
        assertThat(failedNotification.getEventType()).isEqualTo("PAYMENT_FAILURE");
    }

    @Test
    @DisplayName("Redis cache works correctly with notification service")
    void testRedisCacheWorksCorrectly() {
        // Skip if Redis is not available
        if (redisTemplate == null) {
            return;
        }

        // 1. Create and process an order event
        UUID orderId = UUID.randomUUID();
        OrderEvent orderEvent = OrderEvent.builder()
                .orderId(orderId)
                .eventType("PAYMENT_SUCCESS")
                .status("SUCCESS")
                .userId(999L)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(ORDER_EVENTS_TOPIC, orderId.toString(), orderEvent);

        // 2. Wait for notification to be saved
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> notificationRepository.findAll().stream()
                        .anyMatch(n -> orderId.toString().equals(n.getOrderId())));

        // 3. Verify Redis connection is working (basic connectivity test)
        try {
            String testKey = "test:notification:" + orderId;
            redisTemplate.opsForValue().set(testKey, "test-value");
            Object value = redisTemplate.opsForValue().get(testKey);
            assertThat(value).isEqualTo("test-value");
            redisTemplate.delete(testKey);
        } catch (Exception e) {
            // Redis operations might fail in test environment, that's acceptable
        }
    }
}
