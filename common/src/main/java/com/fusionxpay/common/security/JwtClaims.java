package com.fusionxpay.common.security;

public record JwtClaims(Long merchantId, String email, String role) {
}
