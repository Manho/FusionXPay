package com.fusionxpay.ai.common.dto.confirmation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingConfirmationAction {
    private String token;
    private ConfirmationOperationType operationType;
    private ConfirmationStatus status;
    private String summary;
    private Instant expiresAt;
    private Map<String, Object> payload;
}
