package com.fusionxpay.payment.config;

import com.fusionxpay.payment.event.OrderEventProducer;
import com.fusionxpay.payment.service.IdempotencyService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Test configuration to mock external dependencies
 */
@TestConfiguration
public class TestConfig {

    @MockBean
    private OrderEventProducer orderEventProducer;

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, String> redisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }

    @Bean
    public IdempotencyService idempotencyService() {
        return new IdempotencyService(redisTemplate());
    }
}
