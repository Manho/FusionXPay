package com.fusionxpay.order.config;

import com.fusionxpay.order.event.PaymentEventConsumer;
import com.fusionxpay.order.repository.OrderRepository;
import com.fusionxpay.order.service.OrderService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Test configuration to mock external dependencies
 */
@TestConfiguration
public class TestConfig {

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Bean
    @Primary
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return kafkaTemplate;
    }
}
