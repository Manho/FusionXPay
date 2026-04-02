package com.fusionxpay.ai.common.dto.auth.ai;

import com.fusionxpay.ai.common.dto.auth.GatewayMerchantInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTokenResponse {
    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private String refreshToken;
    private String audience;
    private String sessionTokenType;
    private GatewayMerchantInfo merchant;
}
