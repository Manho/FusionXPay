package com.fusionxpay.ai.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CliConfigStore {

    private final CliProperties cliProperties;
    private final ObjectMapper objectMapper;

    public synchronized Optional<CliStoredConfig> load() {
        Path path = cliProperties.getConfigPath();
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            CliStoredConfig config = objectMapper.readValue(path.toFile(), CliStoredConfig.class);
            normalize(config);
            cleanupExpiredConfirmations(config);
            return Optional.of(config);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read CLI config from " + path, ex);
        }
    }

    public synchronized CliStoredConfig loadOrCreate() {
        return load().orElseGet(this::emptyConfig);
    }

    public synchronized void save(CliStoredConfig config) {
        normalize(config);
        cleanupExpiredConfirmations(config);
        Path path = cliProperties.getConfigPath();

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), config);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write CLI config to " + path, ex);
        }
    }

    public synchronized void clearSession() {
        CliStoredConfig config = loadOrCreate();
        config.setJwt(null);
        config.setMerchantEmail(null);
        config.setMerchantId(null);
        config.setMerchantName(null);
        config.setMerchantRole(null);
        config.getPendingConfirmations().clear();

        if (isEffectivelyEmpty(config)) {
            deleteConfigFile();
            return;
        }
        save(config);
    }

    public synchronized Path getConfigPath() {
        return cliProperties.getConfigPath();
    }

    private CliStoredConfig emptyConfig() {
        CliStoredConfig config = new CliStoredConfig();
        normalize(config);
        return config;
    }

    private void normalize(CliStoredConfig config) {
        if (config.getPendingConfirmations() == null) {
            config.setPendingConfirmations(new LinkedHashMap<>());
            return;
        }
        config.setPendingConfirmations(new LinkedHashMap<>(config.getPendingConfirmations()));
    }

    private void cleanupExpiredConfirmations(CliStoredConfig config) {
        Map<String, PendingConfirmationAction> confirmations = config.getPendingConfirmations();
        if (confirmations == null || confirmations.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        confirmations.entrySet().removeIf(entry -> {
            PendingConfirmationAction action = entry.getValue();
            return action == null || action.getExpiresAt() == null || action.getExpiresAt().isBefore(now);
        });
    }

    private boolean isEffectivelyEmpty(CliStoredConfig config) {
        return isBlank(config.getBaseUrl())
                && isBlank(config.getJwt())
                && isBlank(config.getMerchantEmail())
                && config.getMerchantId() == null
                && isBlank(config.getMerchantName())
                && isBlank(config.getMerchantRole())
                && config.getPendingConfirmations().isEmpty();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void deleteConfigFile() {
        try {
            Files.deleteIfExists(cliProperties.getConfigPath());
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to delete CLI config at " + cliProperties.getConfigPath(), ex);
        }
    }
}
