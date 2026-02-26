package com.fusionxpay.api.gateway.service;

import com.fusionxpay.api.gateway.model.MerchantApiKeyRecord;
import com.fusionxpay.api.gateway.model.MerchantStatus;
import com.fusionxpay.api.gateway.model.User;
import com.fusionxpay.api.gateway.repository.MerchantAccountRepository;
import com.fusionxpay.api.gateway.repository.MerchantApiKeyRecordRepository;
import com.fusionxpay.api.gateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyValidationService {

    private final MerchantApiKeyRecordRepository merchantApiKeyRecordRepository;
    private final MerchantAccountRepository merchantAccountRepository;
    private final UserRepository userRepository;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${app.api-key.cache-prefix:gateway:api-key:}")
    private String cachePrefix;

    @Value("${app.api-key.cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    @Value("${app.api-key.legacy-fallback-enabled:true}")
    private boolean legacyFallbackEnabled;

    public Mono<Optional<ApiKeyValidationResult>> resolveMerchant(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Mono.just(Optional.empty());
        }

        String keyHash = sha256(rawApiKey);
        String cacheKey = cachePrefix + keyHash;

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> Mono.just(Optional.of(decodeCacheValue(cached))))
                .switchIfEmpty(resolveFromDatabase(rawApiKey, keyHash)
                        .flatMap(result -> {
                            if (result.isEmpty()) {
                                return Mono.just(result);
                            }
                            String cacheValue = encodeCacheValue(result.get());
                            return redisTemplate.opsForValue()
                                    .set(cacheKey, cacheValue, Duration.ofSeconds(cacheTtlSeconds))
                                    .thenReturn(result);
                        }))
                .onErrorResume(ex -> {
                    log.warn("Failed to resolve API key via cache, fallback to DB: {}", ex.getMessage());
                    return resolveFromDatabase(rawApiKey, keyHash);
                });
    }

    private Mono<Optional<ApiKeyValidationResult>> resolveFromDatabase(String rawApiKey, String keyHash) {
        return Mono.fromCallable(() -> {
                    Optional<MerchantApiKeyRecord> activeKey = merchantApiKeyRecordRepository.findByKeyHashAndActiveTrue(keyHash);
                    if (activeKey.isPresent()) {
                        MerchantApiKeyRecord apiKeyRecord = activeKey.get();
                        boolean activeMerchant = merchantAccountRepository
                                .findByIdAndStatus(apiKeyRecord.getMerchantId(), MerchantStatus.ACTIVE)
                                .isPresent();
                        if (activeMerchant) {
                            return Optional.of(new ApiKeyValidationResult(apiKeyRecord.getMerchantId(), apiKeyRecord.getId(), false));
                        }
                    }

                    if (!legacyFallbackEnabled) {
                        return Optional.<ApiKeyValidationResult>empty();
                    }

                    Optional<User> legacyUser = userRepository.findByApiKey(rawApiKey);
                    if (legacyUser.isEmpty()) {
                        return Optional.<ApiKeyValidationResult>empty();
                    }

                    Optional<Long> merchantId = merchantAccountRepository
                            .findByEmailAndStatus(legacyUser.get().getUsername(), MerchantStatus.ACTIVE)
                            .map(merchant -> merchant.getId());

                    return merchantId.map(id -> new ApiKeyValidationResult(id, null, true));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String encodeCacheValue(ApiKeyValidationResult result) {
        if (result.apiKeyId() == null) {
            return result.merchantId() + "|";
        }
        return result.merchantId() + "|" + result.apiKeyId();
    }

    private ApiKeyValidationResult decodeCacheValue(String value) {
        String[] parts = value.split("\\|", -1);
        Long merchantId = Long.parseLong(parts[0]);
        Long apiKeyId = (parts.length < 2 || parts[1].isBlank()) ? null : Long.parseLong(parts[1]);
        return new ApiKeyValidationResult(merchantId, apiKeyId, false);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash api key", ex);
        }
    }
}
