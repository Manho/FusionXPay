package com.fusionxpay.ai.common.dto.auth.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConsentApproveRequest {
    private String sessionId;
    private String userCode;
}
