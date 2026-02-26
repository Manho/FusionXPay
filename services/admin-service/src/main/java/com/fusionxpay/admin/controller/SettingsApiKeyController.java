package com.fusionxpay.admin.controller;

import com.fusionxpay.admin.dto.ApiKeyInfoResponse;
import com.fusionxpay.admin.dto.ApiKeySecretResponse;
import com.fusionxpay.admin.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings/api-keys")
@RequiredArgsConstructor
@Tag(name = "API Key Settings", description = "Merchant API key management")
public class SettingsApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    @Operation(summary = "Get current API key metadata")
    public ResponseEntity<ApiKeyInfoResponse> getCurrentApiKey(HttpServletRequest request) {
        Long merchantId = getCurrentMerchantId(request);
        return ResponseEntity.ok(apiKeyService.getCurrentApiKeyInfo(merchantId));
    }

    @PostMapping("/rotate")
    @Operation(summary = "Rotate current API key")
    public ResponseEntity<ApiKeySecretResponse> rotateCurrentApiKey(HttpServletRequest request) {
        Long merchantId = getCurrentMerchantId(request);
        return ResponseEntity.ok(apiKeyService.rotateCurrentApiKey(
                merchantId,
                merchantId,
                getClientIp(request),
                request.getHeader("User-Agent")
        ));
    }

    @PostMapping("/reveal")
    @Operation(summary = "Reveal current API key")
    public ResponseEntity<ApiKeySecretResponse> revealCurrentApiKey(HttpServletRequest request) {
        Long merchantId = getCurrentMerchantId(request);
        return ResponseEntity.ok(apiKeyService.revealCurrentApiKey(
                merchantId,
                merchantId,
                getClientIp(request),
                request.getHeader("User-Agent")
        ));
    }

    private Long getCurrentMerchantId(HttpServletRequest request) {
        Object merchantId = request.getAttribute("merchantId");
        if (merchantId == null) {
            throw new IllegalStateException("Missing merchant context");
        }
        return (Long) merchantId;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
