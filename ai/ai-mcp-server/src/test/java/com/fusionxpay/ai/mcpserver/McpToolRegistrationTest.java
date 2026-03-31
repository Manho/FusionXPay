package com.fusionxpay.ai.mcpserver;

import com.fusionxpay.ai.common.client.GatewayClient;
import com.fusionxpay.ai.common.audit.AuditEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.mcp.server.enabled=false",
        "fusionx.ai.audit.enabled=false",
        "fusionx.ai.mcp.auth.jwt-token=test-token"
})
class McpToolRegistrationTest {

    @MockBean
    private GatewayClient gatewayClient;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @Autowired
    private ToolCallbackProvider toolCallbackProvider;

    @Test
    void shouldRegisterAllStageFiveTools() {
        Set<String> toolNames = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .map(toolDefinition -> toolDefinition.name())
                .collect(Collectors.toSet());

        assertThat(toolNames).containsExactlyInAnyOrder(
                "query_payment",
                "search_payments",
                "get_order",
                "search_orders",
                "get_order_status",
                "initiate_payment",
                "refund_payment",
                "confirm_action"
        );
    }

    @Test
    void shouldExposeNamedToolArgumentsForMcpClients() {
        Optional<String> inputSchema = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .filter(toolDefinition -> "search_orders".equals(toolDefinition.name()))
                .map(toolDefinition -> toolDefinition.inputSchema())
                .findFirst();

        assertThat(inputSchema).isPresent();
        assertThat(inputSchema.orElseThrow())
                .contains("\"status\"")
                .contains("\"orderNumber\"")
                .contains("\"from\"")
                .contains("\"to\"")
                .contains("\"page\"")
                .contains("\"size\"")
                .doesNotContain("\"arg0\"")
                .doesNotContain("\"arg1\"");
    }
}
