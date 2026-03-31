package com.fusionxpay.ai.cli.service;

import com.fusionxpay.ai.cli.config.CliConfigStore;
import com.fusionxpay.ai.cli.config.CliStoredConfig;
import com.fusionxpay.ai.common.config.ConfirmationProperties;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationOperationType;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationStatus;
import com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction;
import com.fusionxpay.ai.common.service.ConfirmationLookupResult;
import com.fusionxpay.ai.common.service.ConfirmationLookupStatus;
import com.fusionxpay.ai.common.service.ConfirmationService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class FileBackedConfirmationService implements ConfirmationService {

    private final CliConfigStore configStore;
    private final ConfirmationProperties confirmationProperties;

    public FileBackedConfirmationService(CliConfigStore configStore, ConfirmationProperties confirmationProperties) {
        this.configStore = configStore;
        this.confirmationProperties = confirmationProperties;
    }

    @Override
    public synchronized PendingConfirmationAction create(Long merchantId,
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
                .payload(new LinkedHashMap<>(payload))
                .build();

        // CLI confirmation state is file-backed so `pay/refund` and `confirm` can run in different shell processes.
        configStore.mutate(config -> {
            config.getPendingConfirmations().put(action.getToken(), action);
        });
        return action;
    }

    @Override
    public synchronized ConfirmationLookupResult consume(String token, Long merchantId) {
        return configStore.mutatePreservingExpiredConfirmations(config -> {
            PendingConfirmationAction action = config.getPendingConfirmations().get(token);
            if (action == null) {
                return ConfirmationLookupResult.of(ConfirmationLookupStatus.NOT_FOUND, null);
            }

            if (!action.getMerchantId().equals(merchantId)) {
                return ConfirmationLookupResult.of(ConfirmationLookupStatus.FORBIDDEN, action);
            }

            config.getPendingConfirmations().remove(token);
            if (action.getExpiresAt().isBefore(Instant.now())) {
                action.setStatus(ConfirmationStatus.EXPIRED);
                return ConfirmationLookupResult.of(ConfirmationLookupStatus.EXPIRED, action);
            }

            action.setStatus(ConfirmationStatus.CONFIRMED);
            return ConfirmationLookupResult.of(ConfirmationLookupStatus.READY, action);
        });
    }
}
