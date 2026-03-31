package com.fusionxpay.ai.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.fusionxpay.ai")
@EnableScheduling
public class AiCliApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AiCliApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = application.run(args);
        int exitCode = SpringApplication.exit(context);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
