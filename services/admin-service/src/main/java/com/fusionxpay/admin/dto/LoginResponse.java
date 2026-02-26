package com.fusionxpay.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String tokenType;
    private Long expiresIn;
    private MerchantInfo merchant;
    private String apiKey;

    public static LoginResponse of(String token, Long expiresIn, MerchantInfo merchant) {
        return of(token, expiresIn, merchant, null);
    }

    public static LoginResponse of(String token, Long expiresIn, MerchantInfo merchant, String apiKey) {
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .merchant(merchant)
                .apiKey(apiKey)
                .build();
    }
}
