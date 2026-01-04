package com.fusionxpay.api.gateway;

import com.fusionxpay.api.gateway.config.SecurityConfig;
import com.fusionxpay.api.gateway.config.SwaggerConfig;
import com.fusionxpay.api.gateway.controller.AuthController;
import com.fusionxpay.api.gateway.filter.ApiKeyAuthFilter;
import com.fusionxpay.api.gateway.repository.UserRepository;
import com.fusionxpay.api.gateway.service.UserService;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ApiGatewayApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Test that the application context loads successfully")
    void contextLoads() {
        assertNotNull(applicationContext, "Application context should not be null");
    }

    @Test
    @DisplayName("Test that all required beans are available in the application context")
    void requiredBeansAreAvailable() {
        // Core components
        assertNotNull(applicationContext.getBean(ApiGatewayApplication.class), "ApiGatewayApplication bean should be available");
        assertNotNull(applicationContext.getBean(UserRepository.class), "UserRepository bean should be available");
        assertNotNull(applicationContext.getBean(UserService.class), "UserService bean should be available");

        // Controllers
        assertNotNull(applicationContext.getBean(AuthController.class), "AuthController bean should be available");

        // Filters
        assertNotNull(applicationContext.getBean(ApiKeyAuthFilter.class), "ApiKeyAuthFilter bean should be available");

        // Configuration
        assertNotNull(applicationContext.getBean(SecurityConfig.class), "SecurityConfig bean should be available");
        assertNotNull(applicationContext.getBean(SwaggerConfig.class), "SwaggerConfig bean should be available");
    }

    @Test
    @DisplayName("Test that security components are properly configured")
    void securityComponentsAreConfigured() {
        // Check that the password encoder is available
        assertNotNull(applicationContext.getBean(PasswordEncoder.class), "PasswordEncoder bean should be available");

        // Check that the security web filter chain is available
        assertNotNull(applicationContext.getBean(SecurityWebFilterChain.class), "SecurityWebFilterChain bean should be available");

        // Verify the type of password encoder (should be BCryptPasswordEncoder)
        assertTrue(applicationContext.getBean(PasswordEncoder.class).getClass().getName().contains("BCrypt"),
                "PasswordEncoder should be BCryptPasswordEncoder");
    }

    @Test
    @DisplayName("Test that Swagger/OpenAPI is properly configured")
    void swaggerIsConfigured() {
        // Check that the OpenAPI bean is available
        OpenAPI openAPI = applicationContext.getBean(OpenAPI.class);
        assertNotNull(openAPI, "OpenAPI bean should be available");

        // Verify OpenAPI configuration
        assertNotNull(openAPI.getInfo(), "OpenAPI info should not be null");
        assertEquals("FusionXPay API Gateway", openAPI.getInfo().getTitle(), "OpenAPI title should match");
        assertEquals("v1.0.0", openAPI.getInfo().getVersion(), "OpenAPI version should match");
        assertNotNull(openAPI.getInfo().getContact(), "OpenAPI contact should not be null");
        assertEquals("FusionXPay Support", openAPI.getInfo().getContact().getName(), "OpenAPI contact name should match");
    }
}
