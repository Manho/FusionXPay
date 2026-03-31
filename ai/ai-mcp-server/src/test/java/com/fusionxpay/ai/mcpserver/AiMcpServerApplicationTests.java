package com.fusionxpay.ai.mcpserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.mcp.server.enabled=false",
        "fusionx.ai.audit.enabled=false"
})
class AiMcpServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
