package com.fusionxpay.ai.mcpserver.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.audit.AuditRequestMetadata;
import com.fusionxpay.ai.common.audit.AuditRequestMetadataProvider;
import com.fusionxpay.ai.mcpserver.config.McpSafetyProperties;
import com.fusionxpay.ai.mcpserver.tool.McpToolOperation;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ToolAspectOrderTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
            .withBean(McpSafetyProperties.class)
            .withBean(ToolAspectObserver.class, () -> new RecordingObserver())
            .withBean(AuditRequestMetadataProvider.class, com.fusionxpay.ai.common.audit.ThreadLocalAuditRequestMetadataProvider::new)
            .withBean(InputSafetyAspect.class)
            .withBean(ToolAuditAspect.class)
            .withBean(OutputSafetyAspect.class);

    @Test
    void shouldApplyAspectsInConfiguredOrderAndRedactOutput() {
        contextRunner.run(context -> {
            RecordingObserver observer = context.getBean(RecordingObserver.class);
            AuditRequestMetadataProvider metadataProvider = context.getBean(AuditRequestMetadataProvider.class);
            AtomicReference<AuditRequestMetadata> capturedMetadata = new AtomicReference<>();
            SampleToolTarget target = new SampleToolTarget(metadataProvider, capturedMetadata);

            AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
            proxyFactory.addAspect(context.getBean(InputSafetyAspect.class));
            proxyFactory.addAspect(context.getBean(ToolAuditAspect.class));
            proxyFactory.addAspect(context.getBean(OutputSafetyAspect.class));

            SampleToolTarget proxy = (SampleToolTarget) proxyFactory.getProxy();

            SanitizedPayload payload = proxy.sampleTool("hello");

            assertThat(observer.stages).containsExactly("input:sample_tool", "audit:sample_tool", "output:sample_tool");
            assertThat(payload.secret()).isEqualTo("[REDACTED]");
            assertThat(capturedMetadata.get()).isNotNull();
            assertThat(capturedMetadata.get().source()).isEqualTo("MCP-Java");
            assertThat(capturedMetadata.get().actionName()).isEqualTo("sample_tool");
            assertThat(capturedMetadata.get().correlationId()).isNotBlank();
            assertThat(metadataProvider.currentMetadata()).isNull();
        });
    }

    @Test
    void shouldAllowJsonLikeQuotedInputWithinThreshold() {
        contextRunner.run(context -> {
            SampleToolTarget target = new SampleToolTarget(
                    context.getBean(AuditRequestMetadataProvider.class),
                    new AtomicReference<>()
            );

            AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
            proxyFactory.addAspect(context.getBean(InputSafetyAspect.class));

            SampleToolTarget proxy = (SampleToolTarget) proxyFactory.getProxy();

            assertThatCode(() -> proxy.sampleTool("[\"A\",\"B\"]"))
                    .doesNotThrowAnyException();
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
        private final AuditRequestMetadataProvider metadataProvider;
        private final AtomicReference<AuditRequestMetadata> capturedMetadata;

        SampleToolTarget(AuditRequestMetadataProvider metadataProvider,
                         AtomicReference<AuditRequestMetadata> capturedMetadata) {
            this.metadataProvider = metadataProvider;
            this.capturedMetadata = capturedMetadata;
        }

        @McpToolOperation("sample_tool")
        public SanitizedPayload sampleTool(String input) {
            capturedMetadata.set(metadataProvider.currentMetadata());
            return new SanitizedPayload("Bearer secret-token");
        }
    }

    record SanitizedPayload(String secret) {
    }
}
