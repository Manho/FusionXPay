package com.fusionxpay.ai.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class CliConfigStore {

    private static final Map<Path, ReentrantLock> PROCESS_LOCKS = new ConcurrentHashMap<>();

    private final CliProperties cliProperties;
    private final ObjectMapper objectMapper;

    public synchronized Optional<CliStoredConfig> load() {
        return withConfigLock(this::loadOptionalLocked);
    }

    public synchronized CliStoredConfig loadOrCreate() {
        return load().orElseGet(this::emptyConfig);
    }

    public synchronized void save(CliStoredConfig config) {
        withConfigLock(lock -> {
            persistLocked(config);
            return null;
        });
    }

    public synchronized <T> T mutate(Function<CliStoredConfig, T> mutator) {
        return mutate(mutator, true);
    }

    public synchronized <T> T mutatePreservingExpiredConfirmations(Function<CliStoredConfig, T> mutator) {
        return mutate(mutator, false);
    }

    private <T> T mutate(Function<CliStoredConfig, T> mutator, boolean cleanupExpiredConfirmations) {
        return withConfigLock(lock -> {
            CliStoredConfig config = loadOptionalLocked(lock, cleanupExpiredConfirmations).orElseGet(this::emptyConfig);
            T result = mutator.apply(config);
            persistLocked(config);
            return result;
        });
    }

    public synchronized void mutate(Consumer<CliStoredConfig> mutator) {
        mutate(config -> {
            mutator.accept(config);
            return null;
        });
    }

    public synchronized void clearSession() {
        mutate(config -> {
            config.setJwt(null);
            config.setMerchantEmail(null);
            config.setMerchantId(null);
            config.setMerchantName(null);
            config.setMerchantRole(null);
            config.getPendingConfirmations().clear();
        });
    }

    public synchronized Path getConfigPath() {
        return cliProperties.getConfigPath();
    }

    private CliStoredConfig emptyConfig() {
        CliStoredConfig config = new CliStoredConfig();
        normalize(config);
        return config;
    }

    private boolean normalize(CliStoredConfig config) {
        boolean changed = false;
        if (config.getPendingConfirmations() == null) {
            config.setPendingConfirmations(new LinkedHashMap<>());
            return true;
        }
        if (!(config.getPendingConfirmations() instanceof LinkedHashMap<?, ?>)) {
            config.setPendingConfirmations(new LinkedHashMap<>(config.getPendingConfirmations()));
            changed = true;
        }
        return changed;
    }

    private boolean cleanupExpiredConfirmations(CliStoredConfig config) {
        Map<String, PendingConfirmationAction> confirmations = config.getPendingConfirmations();
        if (confirmations == null || confirmations.isEmpty()) {
            return false;
        }
        Instant now = Instant.now();
        int originalSize = confirmations.size();
        confirmations.entrySet().removeIf(entry -> {
            PendingConfirmationAction action = entry.getValue();
            return action == null || action.getExpiresAt() == null || action.getExpiresAt().isBefore(now);
        });
        return confirmations.size() != originalSize;
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

    private Optional<CliStoredConfig> loadOptionalLocked(FileLock ignored) {
        return loadOptionalLocked(ignored, true);
    }

    private Optional<CliStoredConfig> loadOptionalLocked(FileLock ignored, boolean cleanupExpiredConfirmations) {
        Path path = cliProperties.getConfigPath();
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            CliStoredConfig config = objectMapper.readValue(path.toFile(), CliStoredConfig.class);
            boolean changed = normalize(config);
            if (cleanupExpiredConfirmations) {
                changed = cleanupExpiredConfirmations(config) || changed;
            }
            if (changed) {
                writeConfigFile(path, config);
            }
            return Optional.of(config);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read CLI config from " + path, ex);
        }
    }

    private void persistLocked(CliStoredConfig config) {
        normalize(config);
        cleanupExpiredConfirmations(config);

        if (isEffectivelyEmpty(config)) {
            deleteConfigFile();
            return;
        }

        Path path = cliProperties.getConfigPath();
        try {
            writeConfigFile(path, config);
        } catch (UncheckedIOException ex) {
            throw ex;
        }
    }

    private void writeConfigFile(Path path, CliStoredConfig config) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = parent == null
                    ? Files.createTempFile(path.getFileName().toString(), ".tmp")
                    : Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), config);
                try {
                    Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write CLI config to " + path, ex);
        }
    }

    private <T> T withConfigLock(Function<FileLock, T> callback) {
        Path lockPath = cliProperties.getConfigPath()
                .toAbsolutePath()
                .normalize()
                .resolveSibling(cliProperties.getConfigPath().getFileName() + ".lock");
        ReentrantLock processLock = PROCESS_LOCKS.computeIfAbsent(lockPath, ignored -> new ReentrantLock());
        processLock.lock();
        try {
            Path parent = lockPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
                 FileLock lock = channel.lock()) {
                return callback.apply(lock);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to lock CLI config at " + cliProperties.getConfigPath(), ex);
        } finally {
            processLock.unlock();
        }
    }
}
