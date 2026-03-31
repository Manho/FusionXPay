package com.fusionxpay.ai.cli.config;

import com.fusionxpay.ai.cli.service.FileBackedConfirmationService;
import com.fusionxpay.ai.common.config.ConfirmationProperties;
import com.fusionxpay.ai.common.service.ConfirmationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CliServiceConfiguration {

    @Bean
    ConfirmationService confirmationService(CliConfigStore cliConfigStore, ConfirmationProperties confirmationProperties) {
        return new FileBackedConfirmationService(cliConfigStore, confirmationProperties);
    }
}
