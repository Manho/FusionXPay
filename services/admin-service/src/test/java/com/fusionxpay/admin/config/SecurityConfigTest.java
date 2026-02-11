package com.fusionxpay.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityConfigTest {

    @Test
    void corsConfigurationSource_shouldUseConfiguredOrigins() {
        SecurityConfig securityConfig = new SecurityConfig(
                null,
                null,
                "https://app.example.com, https://admin.example.com"
        );

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

        assertNotNull(configuration);
        assertEquals(
                java.util.List.of("https://app.example.com", "https://admin.example.com"),
                configuration.getAllowedOrigins()
        );
    }

    @Test
    void corsConfigurationSource_shouldFailWhenConfiguredOriginsBlank() {
        SecurityConfig securityConfig = new SecurityConfig(
                null,
                null,
                " , "
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                securityConfig::corsConfigurationSource
        );
        assertEquals("cors.allowed-origins must contain at least one origin", exception.getMessage());
    }
}
