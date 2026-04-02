package com.fusionxpay.ai.common.dto.auth.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAuthorizeResponse {
    private String sessionId;
    private AiAuthFlowMode flowMode;
    private String authorizationUrl;
    private String verificationUrl;
    private String deviceCode;
    private String userCode;
    private Long expiresIn;
    private Long pollIntervalSeconds;
}
