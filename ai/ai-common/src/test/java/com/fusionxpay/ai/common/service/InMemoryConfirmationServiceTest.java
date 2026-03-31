package com.fusionxpay.ai.common.service;

import com.fusionxpay.ai.common.config.ConfirmationProperties;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationOperationType;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationStatus;
import com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryConfirmationServiceTest {

    @Test
    void shouldCreateAndConsumePendingAction() {
        ConfirmationProperties properties = new ConfirmationProperties();
        properties.setTtl(Duration.ofMinutes(5));
        ConfirmationService confirmationService = new InMemoryConfirmationService(properties);

        PendingConfirmationAction action = confirmationService.create(
                11L,
                ConfirmationOperationType.INITIATE_PAYMENT,
                "Initiate payment",
                Map.of("orderId", "123")
        );

        ConfirmationLookupResult result = confirmationService.consume(action.getToken(), 11L);

        assertThat(result.getStatus()).isEqualTo(ConfirmationLookupStatus.READY);
        assertThat(result.getAction()).isNotNull();
        assertThat(result.getAction().getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
    }

    @Test
    void shouldRejectDifferentMerchant() {
        ConfirmationProperties properties = new ConfirmationProperties();
        ConfirmationService confirmationService = new InMemoryConfirmationService(properties);

        PendingConfirmationAction action = confirmationService.create(
                11L,
                ConfirmationOperationType.REFUND_PAYMENT,
                "Refund payment",
                Map.of("transactionId", "tx-1")
        );

        ConfirmationLookupResult result = confirmationService.consume(action.getToken(), 22L);

        assertThat(result.getStatus()).isEqualTo(ConfirmationLookupStatus.FORBIDDEN);
        assertThat(result.getAction()).isNotNull();
        assertThat(result.getAction().getStatus()).isEqualTo(ConfirmationStatus.CONFIRMATION_REQUIRED);
    }

    @Test
    void shouldExpireTimedOutActions() throws InterruptedException {
        ConfirmationProperties properties = new ConfirmationProperties();
        properties.setTtl(Duration.ofMillis(5));
        ConfirmationService confirmationService = new InMemoryConfirmationService(properties);

        PendingConfirmationAction action = confirmationService.create(
                11L,
                ConfirmationOperationType.REFUND_PAYMENT,
                "Refund payment",
                Map.of("transactionId", "tx-1")
        );

        Thread.sleep(15);

        ConfirmationLookupResult result = confirmationService.consume(action.getToken(), 11L);

        assertThat(result.getStatus()).isEqualTo(ConfirmationLookupStatus.EXPIRED);
        assertThat(result.getAction()).isNotNull();
        assertThat(result.getAction().getStatus()).isEqualTo(ConfirmationStatus.EXPIRED);
    }

    @Test
    void shouldCleanupExpiredActionsWithoutExplicitConsume() throws InterruptedException {
        ConfirmationProperties properties = new ConfirmationProperties();
        properties.setTtl(Duration.ofMillis(5));
        InMemoryConfirmationService confirmationService = new InMemoryConfirmationService(properties);

        PendingConfirmationAction action = confirmationService.create(
                11L,
                ConfirmationOperationType.INITIATE_PAYMENT,
                "Initiate payment",
                Map.of("orderId", "123")
        );

        Thread.sleep(15);
        confirmationService.cleanupExpiredActions();

        ConfirmationLookupResult result = confirmationService.consume(action.getToken(), 11L);

        assertThat(result.getStatus()).isEqualTo(ConfirmationLookupStatus.NOT_FOUND);
        assertThat(result.getAction()).isNull();
    }
}
