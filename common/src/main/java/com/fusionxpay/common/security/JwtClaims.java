package com.fusionxpay.common.security;

public record JwtClaims(
        Long merchantId,
        String email,
        String role,
        String audience,
        String tokenType
) {

    public JwtClaims(Long merchantId, String email, String role) {
        this(merchantId, email, role, null, null);
    }
}
