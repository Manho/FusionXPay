package com.fusionxpay.ai.common.dto.auth.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAuthorizeRequest {
    private AiAuthClientType clientType;
    private String audience;
    private AiAuthFlowMode flowMode;
    private String callbackUrl;
    private String state;
    private String codeChallenge;
    private String codeChallengeMethod;
}
