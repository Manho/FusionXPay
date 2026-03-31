package com.fusionxpay.ai.common.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayLoginResponse {
    private String token;
    private String tokenType;
    private Long expiresIn;
    private GatewayMerchantInfo merchant;
}
