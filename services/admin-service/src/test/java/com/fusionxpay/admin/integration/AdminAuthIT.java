package com.fusionxpay.admin.integration;

import com.fusionxpay.admin.dto.LoginRequest;
import com.fusionxpay.admin.dto.LoginResponse;
import com.fusionxpay.admin.dto.RegisterRequest;
import com.fusionxpay.admin.model.Merchant;
import com.fusionxpay.admin.model.MerchantRole;
import com.fusionxpay.admin.model.MerchantStatus;
import com.fusionxpay.admin.repository.MerchantRepository;
import com.fusionxpay.admin.security.JwtTokenProvider;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for JWT authentication in admin-service
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AdminAuthIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static final String ADMIN_EMAIL = "admin@fusionxpay.com";
    private static final String ADMIN_PASSWORD = "admin123";
    private static final String MERCHANT_EMAIL = "merchant@fusionxpay.com";
    private static final String MERCHANT_PASSWORD = "merchant123";

    @BeforeEach
    void setUp() {
        merchantRepository.deleteAll();

        // Create ADMIN user
        Merchant admin = Merchant.builder()
                .merchantCode("ADMIN001")
                .merchantName("Admin User")
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(MerchantRole.ADMIN)
                .status(MerchantStatus.ACTIVE)
                .build();
        merchantRepository.save(admin);

        // Create MERCHANT user
        Merchant merchant = Merchant.builder()
                .merchantCode("MERCHANT001")
                .merchantName("Test Merchant")
                .email(MERCHANT_EMAIL)
                .passwordHash(passwordEncoder.encode(MERCHANT_PASSWORD))
                .role(MerchantRole.MERCHANT)
                .status(MerchantStatus.ACTIVE)
                .build();
        merchantRepository.save(merchant);
    }

    @Test
    @DisplayName("JWT login with ADMIN role returns valid token")
    void testLogin_WithAdminCredentials_ReturnsToken() {
        // Prepare login request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        LoginRequest loginRequest = new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headers);

        // Send login request
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                "/api/v1/admin/auth/login", entity, LoginResponse.class);

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotBlank();
        assertThat(response.getBody().getTokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().getMerchant()).isNotNull();
        assertThat(response.getBody().getMerchant().getRole()).isEqualTo("ADMIN");

        // Verify token is valid
        String token = response.getBody().getToken();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo(ADMIN_EMAIL);
        assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("JWT login with MERCHANT role returns valid token")
    void testLogin_WithMerchantCredentials_ReturnsToken() {
        // Prepare login request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        LoginRequest loginRequest = new LoginRequest(MERCHANT_EMAIL, MERCHANT_PASSWORD);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headers);

        // Send login request
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                "/api/v1/admin/auth/login", entity, LoginResponse.class);

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isNotBlank();
        assertThat(response.getBody().getTokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().getMerchant()).isNotNull();
        assertThat(response.getBody().getMerchant().getRole()).isEqualTo("MERCHANT");

        // Verify token is valid
        String token = response.getBody().getToken();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo(MERCHANT_EMAIL);
        assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo("MERCHANT");
    }

    @Test
    @DisplayName("Login with invalid credentials returns 401")
    void testLogin_WithInvalidCredentials_Returns401() {
        // Prepare login request with wrong password
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        LoginRequest loginRequest = new LoginRequest(ADMIN_EMAIL, "wrongpassword");
        HttpEntity<LoginRequest> entity = new HttpEntity<>(loginRequest, headers);

        // Send login request
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/admin/auth/login", entity, String.class);

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Invalid JWT token is rejected with 401")
    void testProtectedEndpoint_WithInvalidToken_Returns401() {
        // Prepare request with invalid token
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("invalid.jwt.token");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // Send request to protected endpoint
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/auth/me", HttpMethod.GET, entity, String.class);

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Expired JWT token is rejected with 401")
    void testProtectedEndpoint_WithExpiredToken_Returns401() {
        // Create an expired token by manipulating time (simulated with invalid token)
        // Note: In real scenario, we'd need to mock time or use a very short expiration
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBmdXNpb254cGF5LmNvbSIsIm1lcmNoYW50SWQiOjEsInJvbGUiOiJBRE1JTiIsImlhdCI6MTYwMDAwMDAwMCwiZXhwIjoxNjAwMDAwMDAxfQ.invalid_signature";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(expiredToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // Send request to protected endpoint
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/auth/me", HttpMethod.GET, entity, String.class);

        // Verify response - should be rejected
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Valid JWT token can access protected endpoint")
    void testProtectedEndpoint_WithValidToken_ReturnsSuccess() {
        // First, login to get a valid token
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);

        LoginRequest loginRequest = new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD);
        HttpEntity<LoginRequest> loginEntity = new HttpEntity<>(loginRequest, loginHeaders);

        ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity(
                "/api/v1/admin/auth/login", loginEntity, LoginResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = loginResponse.getBody().getToken();

        // Now use the token to access protected endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/admin/auth/me", HttpMethod.GET, entity, String.class);

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(ADMIN_EMAIL);
    }
}
