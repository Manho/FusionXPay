package com.fusionxpay.ai.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationOperationType;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationStatus;
import com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CliConfigStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void clearSessionPreservesBaseUrlButDropsSecretsAndPendingTokens() {
        CliProperties properties = new CliProperties();
        properties.setConfigPath(tempDir.resolve("config.json"));
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        CliConfigStore store = new CliConfigStore(properties, objectMapper);

        CliStoredConfig config = CliStoredConfig.builder()
                .baseUrl("https://api.fusionx.fun")
                .jwt("secret-token")
                .merchantEmail("merchant@example.com")
                .merchantId(42L)
                .merchantName("Merchant")
                .merchantRole("MERCHANT")
                .pendingConfirmations(Map.of(
                        "token-1",
                        PendingConfirmationAction.builder()
                                .token("token-1")
                                .merchantId(42L)
                                .operationType(ConfirmationOperationType.INITIATE_PAYMENT)
                                .status(ConfirmationStatus.CONFIRMATION_REQUIRED)
                                .summary("pending")
                                .expiresAt(Instant.now().plusSeconds(60))
                                .payload(Map.of("k", "v"))
                                .build()
                ))
                .build();
        store.save(config);

        store.clearSession();

        CliStoredConfig saved = store.load().orElseThrow();
        assertThat(saved.getBaseUrl()).isEqualTo("https://api.fusionx.fun");
        assertThat(saved.getJwt()).isNull();
        assertThat(saved.getMerchantEmail()).isNull();
        assertThat(saved.getMerchantId()).isNull();
        assertThat(saved.getPendingConfirmations()).isEmpty();
    }
}
