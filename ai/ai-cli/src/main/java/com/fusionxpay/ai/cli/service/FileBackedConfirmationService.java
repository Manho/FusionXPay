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
        CliStoredConfig config = configStore.loadOrCreate();
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

        config.getPendingConfirmations().put(action.getToken(), action);
        configStore.save(config);
        return action;
    }

    @Override
    public synchronized ConfirmationLookupResult consume(String token, Long merchantId) {
        CliStoredConfig config = configStore.loadOrCreate();
        PendingConfirmationAction action = config.getPendingConfirmations().remove(token);
        configStore.save(config);

        if (action == null) {
            return ConfirmationLookupResult.of(ConfirmationLookupStatus.NOT_FOUND, null);
        }

        if (!action.getMerchantId().equals(merchantId)) {
            config.getPendingConfirmations().put(token, action);
            configStore.save(config);
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
