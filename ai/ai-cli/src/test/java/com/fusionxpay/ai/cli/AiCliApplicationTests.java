package com.fusionxpay.ai.cli;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "fusionx.ai.cli.runner-enabled=false",
        "fusionx.ai.audit.enabled=false"
})
class AiCliApplicationTests {

    @Test
    void contextLoads() {
    }
}
