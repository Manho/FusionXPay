package com.fusionxpay.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "fusionx.ai.audit.enabled=false",
        "fusionx.ai.audit.consumer.enabled=false"
})
class AiAuditConsumerDisabledTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsWithoutRegisteringAuditConsumerBeans() {
        assertThat(applicationContext.containsBean("aiAuditEventConsumer")).isFalse();
        assertThat(applicationContext.containsBean("auditEventKafkaListenerContainerFactory")).isFalse();
    }
}
