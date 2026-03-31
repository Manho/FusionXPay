package com.fusionxpay.ai.mcpserver.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.mcpserver.config.McpSafetyProperties;
import com.fusionxpay.ai.mcpserver.tool.McpToolOperation;
import com.fusionxpay.ai.mcpserver.tool.UnsafeToolInputException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputSafetyAspectTest {

    @Test
    void shouldUseCompiledPatternCacheAfterConstruction() {
        McpSafetyProperties properties = new McpSafetyProperties();
        properties.setBlockedPatterns(new ArrayList<>(List.of("(?i)drop\\s+table")));

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        ObjectProvider<ToolAspectObserver> provider = beanFactory.getBeanProvider(ToolAspectObserver.class);
        InputSafetyAspect aspect = new InputSafetyAspect(new ObjectMapper().findAndRegisterModules(), properties, provider);
        properties.getBlockedPatterns().clear();

        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new SampleToolTarget());
        proxyFactory.addAspect(aspect);
        SampleToolTarget proxy = proxyFactory.getProxy();

        assertThatThrownBy(() -> proxy.sampleTool("DROP TABLE payments"))
                .isInstanceOf(UnsafeToolInputException.class)
                .hasMessageContaining("blocked safety pattern");
    }

    static class SampleToolTarget {

        @McpToolOperation("sample_tool")
        public String sampleTool(String input) {
            return input;
        }
    }
}
