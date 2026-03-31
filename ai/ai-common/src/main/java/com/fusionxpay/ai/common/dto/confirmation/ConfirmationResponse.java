package com.fusionxpay.ai.common.dto.confirmation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationResponse {
    private ConfirmationStatus status;
    private String token;
    private ConfirmationOperationType operationType;
    private String summary;
    private Instant expiresAt;
    private Object result;
}
