package com.fusionxpay.admin.service;

import com.fusionxpay.admin.dto.ApiKeyInfoResponse;
import com.fusionxpay.admin.dto.ApiKeySecretResponse;
import com.fusionxpay.admin.exception.ResourceNotFoundException;
import com.fusionxpay.admin.model.MerchantApiKey;
import com.fusionxpay.admin.model.MerchantApiKeyAudit;
import com.fusionxpay.admin.repository.MerchantApiKeyAuditRepository;
import com.fusionxpay.admin.repository.MerchantApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final MerchantApiKeyRepository merchantApiKeyRepository;
    private final MerchantApiKeyAuditRepository merchantApiKeyAuditRepository;
    private final ApiKeyCryptoService apiKeyCryptoService;

    @Transactional(readOnly = true)
    public ApiKeyInfoResponse getCurrentApiKeyInfo(Long merchantId) {
        MerchantApiKey apiKey = merchantApiKeyRepository.findByMerchantIdAndActiveTrue(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("No active API key found"));
        return ApiKeyInfoResponse.fromEntity(apiKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyInfoResponse> getMerchantApiKeys(Long merchantId) {
        return merchantApiKeyRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)
                .stream()
                .map(ApiKeyInfoResponse::fromEntity)
                .toList();
    }

    @Transactional
    public String createInitialApiKey(Long merchantId, Long actorMerchantId, String ip, String userAgent) {
        return createAndPersistApiKey(merchantId, actorMerchantId, ip, userAgent, "GENERATE");
    }

    @Transactional
    public ApiKeySecretResponse rotateCurrentApiKey(Long merchantId, Long actorMerchantId, String ip, String userAgent) {
        merchantApiKeyRepository.findByMerchantIdAndActiveTrue(merchantId).ifPresent(existing -> {
            existing.setActive(false);
            existing.setRevokedAt(LocalDateTime.now());
            existing.setRevokedBy(actorMerchantId);
            merchantApiKeyRepository.save(existing);
            writeAudit(merchantId, existing.getId(), actorMerchantId, "REVOKE", ip, userAgent);
        });

        String newApiKey = createAndPersistApiKey(merchantId, actorMerchantId, ip, userAgent, "ROTATE");
        return ApiKeySecretResponse.builder().apiKey(newApiKey).build();
    }

    @Transactional
    public ApiKeySecretResponse revealCurrentApiKey(Long merchantId, Long actorMerchantId, String ip, String userAgent) {
        MerchantApiKey apiKey = merchantApiKeyRepository.findByMerchantIdAndActiveTrue(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("No active API key found"));
        String plaintext = apiKeyCryptoService.decrypt(apiKey.getKeyEncrypted());

        writeAudit(merchantId, apiKey.getId(), actorMerchantId, "REVEAL", ip, userAgent);
        return ApiKeySecretResponse.builder().apiKey(plaintext).build();
    }

    @Transactional
    public ApiKeySecretResponse revealMerchantApiKey(Long merchantId, Long actorMerchantId, String ip, String userAgent) {
        return revealCurrentApiKey(merchantId, actorMerchantId, ip, userAgent);
    }

    private String createAndPersistApiKey(Long merchantId,
                                          Long actorMerchantId,
                                          String ip,
                                          String userAgent,
                                          String action) {
        String plaintext = generateApiKey();
        String hash = apiKeyCryptoService.sha256(plaintext);
        String encrypted = apiKeyCryptoService.encrypt(plaintext);

        MerchantApiKey entity = MerchantApiKey.builder()
                .merchantId(merchantId)
                .keyPrefix(plaintext.substring(0, 12))
                .lastFour(plaintext.substring(plaintext.length() - 4))
                .keyHash(hash)
                .keyEncrypted(encrypted)
                .active(true)
                .createdBy(actorMerchantId)
                .build();

        entity = merchantApiKeyRepository.save(entity);
        writeAudit(merchantId, entity.getId(), actorMerchantId, action, ip, userAgent);
        return plaintext;
    }

    private String generateApiKey() {
        return "fxp_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().substring(0, 8);
    }

    private void writeAudit(Long merchantId,
                            Long apiKeyId,
                            Long actorMerchantId,
                            String action,
                            String ip,
                            String userAgent) {
        MerchantApiKeyAudit audit = MerchantApiKeyAudit.builder()
                .merchantId(merchantId)
                .apiKeyId(apiKeyId)
                .actorMerchantId(actorMerchantId)
                .action(action)
                .ip(ip)
                .userAgent(userAgent)
                .build();
        merchantApiKeyAuditRepository.save(audit);
    }
}
