package com.fusionxpay.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.admin.dto.LoginRequest;
import com.fusionxpay.admin.dto.LoginResponse;
import com.fusionxpay.admin.dto.MerchantInfo;
import com.fusionxpay.admin.model.MerchantRole;
import com.fusionxpay.admin.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.fusionxpay.admin.AdminApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController
 */
@SpringBootTest(classes = AdminApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void login_Success_ReturnsToken() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        MerchantInfo merchantInfo = MerchantInfo.builder()
                .id(1L)
                .merchantCode("MCH001")
                .merchantName("Test Merchant")
                .email("test@example.com")
                .role(MerchantRole.MERCHANT)
                .build();
        LoginResponse response = LoginResponse.of("jwt.token.here", 86400L, merchantInfo);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.merchant.email").value("test@example.com"));
    }

    @Test
    void login_InvalidCredentials_Returns401() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        // When/Then
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"));
    }

    @Test
    void login_InvalidEmail_Returns400() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("invalid-email", "password123");

        // When/Then
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void login_EmptyPassword_Returns400() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "");

        // When/Then
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
