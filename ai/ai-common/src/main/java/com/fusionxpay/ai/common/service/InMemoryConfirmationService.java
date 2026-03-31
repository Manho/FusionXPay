package com.fusionxpay.ai.common.service;

import com.fusionxpay.ai.common.config.ConfirmationProperties;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationOperationType;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationStatus;
import com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryConfirmationService implements ConfirmationService {

    private final ConfirmationProperties confirmationProperties;
    private final ConcurrentMap<String, PendingConfirmationAction> pendingActions = new ConcurrentHashMap<>();

    public InMemoryConfirmationService(ConfirmationProperties confirmationProperties) {
        this.confirmationProperties = confirmationProperties;
    }

    @Override
    public PendingConfirmationAction create(Long merchantId,
                                            ConfirmationOperationType operationType,
                                            String summary,
                                            Map<String, Object> payload) {
        Instant expiresAt = Instant.now().plus(confirmationProperties.getTtl());
        PendingConfirmationAction action = PendingConfirmationAction.builder()
                .token(UUID.randomUUID().toString())
                .merchantId(merchantId)
                .operationType(operationType)
                .status(ConfirmationStatus.CONFIRMATION_REQUIRED)
                .summary(summary)
                .expiresAt(expiresAt)
                .payload(payload)
                .build();
        pendingActions.put(action.getToken(), action);
        return action;
    }

    @Override
    public ConfirmationLookupResult consume(String token, Long merchantId) {
        PendingConfirmationAction action = pendingActions.remove(token);
        if (action == null) {
            return ConfirmationLookupResult.of(ConfirmationLookupStatus.NOT_FOUND, null);
        }

        if (!action.getMerchantId().equals(merchantId)) {
            pendingActions.put(action.getToken(), action);
            return ConfirmationLookupResult.of(ConfirmationLookupStatus.FORBIDDEN, action);
        }

        if (action.getExpiresAt().isBefore(Instant.now())) {
            action.setStatus(ConfirmationStatus.EXPIRED);
            return ConfirmationLookupResult.of(ConfirmationLookupStatus.EXPIRED, action);
        }

        action.setStatus(ConfirmationStatus.CONFIRMED);
        return ConfirmationLookupResult.of(ConfirmationLookupStatus.READY, action);
    }
}
