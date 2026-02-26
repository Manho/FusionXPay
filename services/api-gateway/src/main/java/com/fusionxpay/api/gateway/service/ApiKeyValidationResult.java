package com.fusionxpay.api.gateway.service;

public record ApiKeyValidationResult(Long merchantId, Long apiKeyId, boolean legacyMatched) {
}
