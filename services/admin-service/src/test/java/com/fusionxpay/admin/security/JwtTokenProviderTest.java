package com.fusionxpay.admin.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JwtTokenProvider
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_SECRET = "fusionxpay-admin-jwt-secret-key-must-be-at-least-256-bits-long-for-testing";
    private static final Long TEST_EXPIRATION = 86400000L; // 24 hours

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", TEST_EXPIRATION);
        jwtTokenProvider.init();
    }

    @Test
    void generateToken_ShouldReturnValidToken() {
        // Given
        Long merchantId = 1L;
        String email = "test@example.com";
        String role = "MERCHANT";

        // When
        String token = jwtTokenProvider.generateToken(merchantId, email, role);

        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    void getEmailFromToken_ShouldReturnCorrectEmail() {
        // Given
        String email = "merchant@example.com";
        String token = jwtTokenProvider.generateToken(1L, email, "MERCHANT");

        // When
        String extractedEmail = jwtTokenProvider.getEmailFromToken(token);

        // Then
        assertThat(extractedEmail).isEqualTo(email);
    }

    @Test
    void getMerchantIdFromToken_ShouldReturnCorrectId() {
        // Given
        Long merchantId = 123L;
        String token = jwtTokenProvider.generateToken(merchantId, "test@example.com", "ADMIN");

        // When
        Long extractedId = jwtTokenProvider.getMerchantIdFromToken(token);

        // Then
        assertThat(extractedId).isEqualTo(merchantId);
    }

    @Test
    void getRoleFromToken_ShouldReturnCorrectRole() {
        // Given
        String role = "ADMIN";
        String token = jwtTokenProvider.generateToken(1L, "admin@example.com", role);

        // When
        String extractedRole = jwtTokenProvider.getRoleFromToken(token);

        // Then
        assertThat(extractedRole).isEqualTo(role);
    }

    @Test
    void validateToken_ShouldReturnTrue_ForValidToken() {
        // Given
        String token = jwtTokenProvider.generateToken(1L, "test@example.com", "MERCHANT");

        // When
        boolean isValid = jwtTokenProvider.validateToken(token);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void validateToken_ShouldReturnFalse_ForInvalidToken() {
        // Given
        String invalidToken = "invalid.jwt.token";

        // When
        boolean isValid = jwtTokenProvider.validateToken(invalidToken);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void validateToken_ShouldReturnFalse_ForEmptyToken() {
        // When
        boolean isValid = jwtTokenProvider.validateToken("");

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void getExpirationInSeconds_ShouldReturnCorrectValue() {
        // When
        Long expirationSeconds = jwtTokenProvider.getExpirationInSeconds();

        // Then
        assertThat(expirationSeconds).isEqualTo(TEST_EXPIRATION / 1000);
    }
}
