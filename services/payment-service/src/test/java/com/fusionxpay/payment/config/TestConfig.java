package com.fusionxpay.payment.config;

import com.fusionxpay.payment.event.OrderEventProducer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Test configuration to mock external dependencies
 */
@TestConfiguration
public class TestConfig {

    @MockBean
    private OrderEventProducer orderEventProducer;
}
