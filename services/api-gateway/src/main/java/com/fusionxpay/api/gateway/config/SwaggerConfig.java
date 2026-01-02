package com.fusionxpay.api.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI fusionXPayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FusionXPay API Gateway")
                        .description("API documentation for the API Gateway of the FusionXPay platform")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("FusionXPay Support")
                                .url("https://fusionxpay.com/support")
                                .email("support@fusionxpay.com"))
                        .license(new License()
                                .name("FusionXPay License")
                                .url("https://fusionxpay.com/license")));
    }
}
