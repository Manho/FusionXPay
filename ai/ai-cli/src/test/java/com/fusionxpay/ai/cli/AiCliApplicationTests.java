package com.fusionxpay.ai.cli;

import com.fusionxpay.ai.common.audit.AuditRequestMetadataProvider;
import com.fusionxpay.ai.common.audit.ThreadLocalAuditRequestMetadataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "fusionx.ai.cli.runner-enabled=false"
})
class AiCliApplicationTests {

    @Autowired
    private AuditRequestMetadataProvider auditRequestMetadataProvider;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldExposeThreadLocalAuditMetadataProvider() {
        assertThat(auditRequestMetadataProvider).isInstanceOf(ThreadLocalAuditRequestMetadataProvider.class);
    }
}
