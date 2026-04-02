package com.fusionxpay.ai.common.dto.auth.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConsentApproveResponse {
    private AiAuthFlowMode flowMode;
    private boolean approved;
    private String redirectUrl;
    private String message;
}
