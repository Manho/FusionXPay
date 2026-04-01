package com.fusionxpay.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "fusionx.ai.audit.enabled=false",
        "fusionx.ai.audit.consumer.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:audit-disabled-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@ActiveProfiles("test")
class AiAuditConsumerDisabledTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsWithoutRegisteringAuditConsumerBeans() {
        assertThat(applicationContext.containsBean("aiAuditEventConsumer")).isFalse();
        assertThat(applicationContext.containsBean("auditEventKafkaListenerContainerFactory")).isFalse();
    }
}
