package com.fusionxpay.ai.cli.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fusionxpay.ai.cli.config.CliConfigStore;
import com.fusionxpay.ai.cli.config.CliProperties;
import com.fusionxpay.ai.cli.config.CliStoredConfig;
import com.fusionxpay.ai.common.config.ConfirmationProperties;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationOperationType;
import com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction;
import com.fusionxpay.ai.common.service.ConfirmationLookupStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileBackedConfirmationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void confirmationTokensPersistAcrossServiceInstances() {
        CliProperties cliProperties = new CliProperties();
        cliProperties.setConfigPath(tempDir.resolve("config.json"));
        CliConfigStore store = new CliConfigStore(cliProperties, new ObjectMapper().registerModule(new JavaTimeModule()));
        ConfirmationProperties confirmationProperties = new ConfirmationProperties();
        confirmationProperties.setTtl(Duration.ofMinutes(10));

        FileBackedConfirmationService first = new FileBackedConfirmationService(store, confirmationProperties);
        String token = first.create(7L, ConfirmationOperationType.INITIATE_PAYMENT, "pay", Map.of("orderId", "o-1")).getToken();

        FileBackedConfirmationService second = new FileBackedConfirmationService(store, confirmationProperties);
        var lookup = second.consume(token, 7L);

        assertThat(lookup.getStatus()).isEqualTo(ConfirmationLookupStatus.READY);
        assertThat(lookup.getAction().getSummary()).isEqualTo("pay");
        assertThat(store.load()).isEmpty();
    }

    @Test
    void consumeReturnsForbiddenWhenMerchantDoesNotOwnToken() {
        CliProperties cliProperties = new CliProperties();
        cliProperties.setConfigPath(tempDir.resolve("config.json"));
        CliConfigStore store = new CliConfigStore(cliProperties, new ObjectMapper().registerModule(new JavaTimeModule()));
        ConfirmationProperties confirmationProperties = new ConfirmationProperties();
        confirmationProperties.setTtl(Duration.ofMinutes(10));

        FileBackedConfirmationService service = new FileBackedConfirmationService(store, confirmationProperties);
        String token = service.create(9L, ConfirmationOperationType.REFUND_PAYMENT, "refund", Map.of()).getToken();

        var lookup = service.consume(token, 8L);

        assertThat(lookup.getStatus()).isEqualTo(ConfirmationLookupStatus.FORBIDDEN);
        assertThat(store.load().orElseThrow().getPendingConfirmations()).containsKey(token);
    }

    @Test
    void consumeReturnsExpiredAndRemovesExpiredToken() throws Exception {
        CliProperties cliProperties = new CliProperties();
        cliProperties.setConfigPath(tempDir.resolve("config.json"));
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        CliConfigStore store = new CliConfigStore(cliProperties, objectMapper);
        ConfirmationProperties confirmationProperties = new ConfirmationProperties();
        confirmationProperties.setTtl(Duration.ofMinutes(10));

        FileBackedConfirmationService service = new FileBackedConfirmationService(store, confirmationProperties);
        String token = service.create(9L, ConfirmationOperationType.REFUND_PAYMENT, "refund", Map.of()).getToken();
        CliStoredConfig storedConfig = objectMapper.readValue(cliProperties.getConfigPath().toFile(), CliStoredConfig.class);
        PendingConfirmationAction action = storedConfig.getPendingConfirmations().get(token);
        action.setExpiresAt(Instant.now().minusSeconds(60));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(cliProperties.getConfigPath().toFile(), storedConfig);

        var lookup = service.consume(token, 9L);

        assertThat(lookup.getStatus()).isEqualTo(ConfirmationLookupStatus.EXPIRED);
        assertThat(store.load()).isEmpty();
    }
}
