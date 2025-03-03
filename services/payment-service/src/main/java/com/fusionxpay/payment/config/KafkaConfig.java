package com.fusionxpay.payment.config;

import com.fusionxpay.payment.event.OrderPaymentEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.payment-events}")
    private String paymentEventsTopic;

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(paymentEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public KafkaTemplate<String, OrderPaymentEvent> kafkaTemplate(
            ProducerFactory<String, OrderPaymentEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
