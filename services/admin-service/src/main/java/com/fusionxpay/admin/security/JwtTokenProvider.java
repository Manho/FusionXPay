package com.fusionxpay.admin.security;

import com.fusionxpay.common.security.JwtClaims;
import com.fusionxpay.common.security.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT Token Provider - handles token generation and validation
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final JwtUtils jwtUtils;
    private final Long jwtExpiration;

    public JwtTokenProvider(@Value("${jwt.secret}") String jwtSecret,
                            @Value("${jwt.expiration}") Long jwtExpiration) {
        this.jwtUtils = new JwtUtils(jwtSecret);
        this.jwtExpiration = jwtExpiration;
    }

    /**
     * Generate JWT token for merchant
     */
    public String generateToken(Long merchantId, String email, String role) {
        return jwtUtils.generateToken(new JwtClaims(merchantId, email, role), jwtExpiration);
    }

    /**
     * Get email from JWT token
     */
    public String getEmailFromToken(String token) {
        return jwtUtils.parseClaims(token).email();
    }

    /**
     * Get merchant ID from JWT token
     */
    public Long getMerchantIdFromToken(String token) {
        return jwtUtils.parseClaims(token).merchantId();
    }

    /**
     * Get role from JWT token
     */
    public String getRoleFromToken(String token) {
        return jwtUtils.parseClaims(token).role();
    }

    /**
     * Validate JWT token
     */
    public boolean validateToken(String token) {
        try {
            return jwtUtils.validateToken(token);
        } catch (RuntimeException ex) {
            log.error("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Get token expiration time in seconds
     */
    public Long getExpirationInSeconds() {
        return jwtExpiration / 1000;
    }
}
