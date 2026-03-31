package com.fusionxpay.ai.mcpserver.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.audit.AuditEvent;
import com.fusionxpay.ai.common.audit.AuditEventPublisher;
import com.fusionxpay.ai.common.dto.auth.GatewayMerchantInfo;
import com.fusionxpay.ai.common.dto.auth.MerchantSession;
import com.fusionxpay.ai.mcpserver.config.McpSafetyProperties;
import com.fusionxpay.ai.mcpserver.tool.McpAuthenticationService;
import com.fusionxpay.ai.mcpserver.tool.McpToolOperation;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolAspectOrderTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
            .withBean(McpSafetyProperties.class)
            .withBean(ToolAspectObserver.class, () -> new RecordingObserver())
            .withBean(McpAuthenticationService.class, () -> {
                McpAuthenticationService authenticationService = mock(McpAuthenticationService.class);
                when(authenticationService.getCurrentMerchantIdOrNull()).thenReturn(42L);
                when(authenticationService.getCurrentSession()).thenReturn(MerchantSession.builder()
                        .token("jwt-token")
                        .merchant(GatewayMerchantInfo.builder().id(42L).role("MERCHANT").build())
                        .build());
                return authenticationService;
            })
            .withBean(AuditEventPublisher.class, () -> {
                AtomicReference<AuditEvent> holder = new AtomicReference<>();
                return holder::set;
            })
            .withBean(InputSafetyAspect.class)
            .withBean(ToolAuditAspect.class)
            .withBean(OutputSafetyAspect.class);

    @Test
    void shouldApplyAspectsInConfiguredOrderAndRedactOutput() {
        contextRunner.run(context -> {
            RecordingObserver observer = context.getBean(RecordingObserver.class);
            SampleToolTarget target = new SampleToolTarget();

            AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
            proxyFactory.addAspect(context.getBean(InputSafetyAspect.class));
            proxyFactory.addAspect(context.getBean(ToolAuditAspect.class));
            proxyFactory.addAspect(context.getBean(OutputSafetyAspect.class));

            SampleToolTarget proxy = (SampleToolTarget) proxyFactory.getProxy();

            SanitizedPayload payload = proxy.sampleTool("hello");

            assertThat(observer.stages).containsExactly("input:sample_tool", "audit:sample_tool", "output:sample_tool");
            assertThat(payload.secret()).isEqualTo("[REDACTED]");
        });
    }

    static class RecordingObserver implements ToolAspectObserver {
        private final List<String> stages = new ArrayList<>();

        @Override
        public void onEnter(String stage, String toolName) {
            stages.add(stage + ":" + toolName);
        }
    }

    static class SampleToolTarget {

        @McpToolOperation("sample_tool")
        public SanitizedPayload sampleTool(String input) {
            return new SanitizedPayload("Bearer secret-token");
        }
    }

    record SanitizedPayload(String secret) {
    }
}
