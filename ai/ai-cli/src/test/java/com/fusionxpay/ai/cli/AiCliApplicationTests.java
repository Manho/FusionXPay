package com.fusionxpay.ai.cli;

import com.fusionxpay.ai.common.audit.AuditEventPublisher;
import com.fusionxpay.ai.common.audit.NoopAuditEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "fusionx.ai.cli.runner-enabled=false",
        "fusionx.ai.audit.enabled=false"
})
class AiCliApplicationTests {

    @Autowired
    private AuditEventPublisher auditEventPublisher;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldUseNoopAuditPublisherWhenAuditIsDisabled() {
        assertThat(auditEventPublisher).isInstanceOf(NoopAuditEventPublisher.class);
    }
}
