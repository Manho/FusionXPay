package com.fusionxpay.ai.common.dto.auth.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPollResponse {
    private AiAuthPollStatus status;
    private Long pollIntervalSeconds;
    private String message;
}
