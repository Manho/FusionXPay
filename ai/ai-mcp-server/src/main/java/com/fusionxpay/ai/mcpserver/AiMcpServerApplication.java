package com.fusionxpay.ai.mcpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.fusionxpay.ai")
public class AiMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AiMcpServerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }
}
