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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Test
    void concurrentWritersKeepConfigFileReadable() throws Exception {
        Path configPath = tempDir.resolve("config.json");
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        CliProperties firstProperties = new CliProperties();
        firstProperties.setConfigPath(configPath);
        CliProperties secondProperties = new CliProperties();
        secondProperties.setConfigPath(configPath);

        CliConfigStore firstStore = new CliConfigStore(firstProperties, objectMapper);
        CliConfigStore secondStore = new CliConfigStore(secondProperties, objectMapper);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            tasks.add(() -> {
                startLatch.await();
                for (int i = 0; i < 20; i++) {
                    firstStore.save(CliStoredConfig.builder()
                            .baseUrl("https://api.fusionx.fun")
                            .merchantEmail("first-" + i + "@example.com")
                            .merchantId(1L)
                            .build());
                }
                return null;
            });
            tasks.add(() -> {
                startLatch.await();
                for (int i = 0; i < 20; i++) {
                    secondStore.save(CliStoredConfig.builder()
                            .baseUrl("https://api.fusionx.fun")
                            .merchantEmail("second-" + i + "@example.com")
                            .merchantId(2L)
                            .build());
                }
                return null;
            });

            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(executor.submit(task));
            }
            startLatch.countDown();
            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        CliStoredConfig storedConfig = objectMapper.readValue(configPath.toFile(), CliStoredConfig.class);
        assertThat(storedConfig.getBaseUrl()).isEqualTo("https://api.fusionx.fun");
        assertThat(storedConfig.getMerchantEmail()).isIn(
                "first-19@example.com",
                "second-19@example.com"
        );
        assertThat(storedConfig.getMerchantId()).isIn(1L, 2L);
    }
}
