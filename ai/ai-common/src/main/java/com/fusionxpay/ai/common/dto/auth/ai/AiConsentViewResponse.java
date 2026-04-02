package com.fusionxpay.ai.common.dto.auth.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConsentViewResponse {
    private String sessionId;
    private String userCode;
    private AiAuthClientType clientType;
    private String audience;
    private AiAuthFlowMode flowMode;
    private String merchantEmail;
    private String merchantName;
    private Long expiresIn;
    private String callbackDisplay;
}
