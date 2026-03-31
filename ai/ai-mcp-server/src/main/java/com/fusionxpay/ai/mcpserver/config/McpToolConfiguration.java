package com.fusionxpay.ai.mcpserver.config;

import com.fusionxpay.ai.mcpserver.tool.FusionXMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({McpAuthProperties.class, McpSafetyProperties.class})
public class McpToolConfiguration {

    @Bean
    ToolCallbackProvider fusionxMcpToolCallbackProvider(FusionXMcpTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
