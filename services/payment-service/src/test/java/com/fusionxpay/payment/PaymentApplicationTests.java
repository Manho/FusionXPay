package com.fusionxpay.payment;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.config.TestConfig;
import com.fusionxpay.payment.controller.PaymentController;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.provider.PaymentProviderFactory;
import com.fusionxpay.payment.repository.PaymentTransactionRepository;
import com.fusionxpay.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        // Keep unit-style SpringBoot tests self-contained (no Eureka/Kafka/MySQL required).
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "fusionxpay.kafka.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:payment_service_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@Import(TestConfig.class)
class PaymentApplicationTests {

    @Autowired
    private PaymentController paymentController;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentProviderFactory paymentProviderFactory;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    // RedisTemplate is already mocked in TestConfig

    // OrderEventProducer is already mocked in TestConfig

    @Test
    void contextLoads() {
        // Verify that the context loads and key beans are available
        assertNotNull(paymentController);
        assertNotNull(paymentService);
        assertNotNull(paymentProviderFactory);
        assertNotNull(paymentTransactionRepository);
    }

    @Test
    void testPaymentProvidersAvailable() {
        // Test that the payment service returns the available payment providers
        var providers = paymentService.getAvailablePaymentProviders();
        assertNotNull(providers);
        assert(providers.contains("STRIPE"));
        assert(providers.contains("PAYPAL"));
    }

    @Test
    void testPaymentProviderFactory() {
        // Test that the payment provider factory returns the correct providers
        var stripeProvider = paymentProviderFactory.getProvider("STRIPE");
        var paypalProvider = paymentProviderFactory.getProvider("PAYPAL");

        assertNotNull(stripeProvider);
        assertNotNull(paypalProvider);
        assert("STRIPE".equalsIgnoreCase(stripeProvider.getProviderName()));
        assert("PAYPAL".equalsIgnoreCase(paypalProvider.getProviderName()));
    }

    @Test
    void testGetPaymentTransactionByOrderId_NotFound() {
        // Given
        UUID orderId = UUID.randomUUID();
        paymentTransactionRepository.deleteAll();

        // When
        PaymentResponse response = paymentService.getPaymentTransactionByOrderId(orderId);

        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.NOT_FOUND, response.getStatus());
        assertEquals(orderId, response.getOrderId());
    }

    @Test
    void testGetPaymentTransactionByOrderId_Found() {
        // Given
        UUID orderId = UUID.randomUUID();
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.SUCCESS.name());

        PaymentTransaction saved = paymentTransactionRepository.save(transaction);

        // When
        PaymentResponse response = paymentService.getPaymentTransactionByOrderId(orderId);

        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals(orderId, response.getOrderId());
        assertEquals(saved.getTransactionId(), response.getTransactionId());
    }
}
