package com.fusionxpay.ai.common.service;

import com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationLookupResult {
    private ConfirmationLookupStatus status;
    private PendingConfirmationAction action;

    public static ConfirmationLookupResult of(ConfirmationLookupStatus status, PendingConfirmationAction action) {
        return ConfirmationLookupResult.builder()
                .status(status)
                .action(action)
                .build();
    }
}
