package com.fusionxpay.admin.controller;

import com.fusionxpay.ai.common.dto.auth.ai.AiAuthorizeRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthorizeResponse;
import com.fusionxpay.ai.common.dto.auth.ai.AiConsentApproveRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiConsentApproveResponse;
import com.fusionxpay.ai.common.dto.auth.ai.AiConsentViewResponse;
import com.fusionxpay.ai.common.dto.auth.ai.AiPollRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiPollResponse;
import com.fusionxpay.ai.common.dto.auth.ai.AiRefreshRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiRevokeRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiTokenExchangeRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiTokenResponse;
import com.fusionxpay.admin.dto.MerchantInfo;
import com.fusionxpay.admin.service.AiAuthSessionService;
import com.fusionxpay.admin.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth/ai")
@RequiredArgsConstructor
@Tag(name = "AI Authentication", description = "Browser auth endpoints for CLI and MCP clients")
public class AiAuthController {

    private final AiAuthSessionService aiAuthSessionService;
    private final AuthService authService;

    @PostMapping("/authorize")
    @Operation(summary = "Create AI auth session", description = "Start browser authorization for CLI or MCP")
    public ResponseEntity<AiAuthorizeResponse> authorize(@Valid @RequestBody AiAuthorizeRequest request) {
        return ResponseEntity.ok(aiAuthSessionService.authorize(request));
    }

    @GetMapping("/consent")
    @Operation(summary = "Get AI consent session", description = "Resolve consent details for callback or device-code approval")
    public ResponseEntity<AiConsentViewResponse> consent(@RequestParam(required = false) String sessionId,
                                                         @RequestParam(required = false) String userCode) {
        return ResponseEntity.ok(aiAuthSessionService.getConsent(sessionId, userCode, currentMerchant()));
    }

    @PostMapping("/consent/approve")
    @Operation(summary = "Approve AI auth session", description = "Approve first-party browser auth for CLI or MCP")
    public ResponseEntity<AiConsentApproveResponse> approve(@RequestBody AiConsentApproveRequest request) {
        return ResponseEntity.ok(aiAuthSessionService.approve(request.getSessionId(), request.getUserCode(), currentMerchant()));
    }

    @PostMapping("/token")
    @Operation(summary = "Exchange AI auth token", description = "Exchange authorization code or device code for session tokens")
    public ResponseEntity<AiTokenResponse> token(@RequestBody AiTokenExchangeRequest request) {
        return ResponseEntity.ok(aiAuthSessionService.exchange(request));
    }

    @PostMapping("/poll")
    @Operation(summary = "Poll AI auth session", description = "Poll device-code authorization status")
    public ResponseEntity<AiPollResponse> poll(@RequestBody AiPollRequest request) {
        return ResponseEntity.ok(aiAuthSessionService.poll(request.getDeviceCode()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh AI session token", description = "Rotate access and refresh tokens for CLI/MCP")
    public ResponseEntity<AiTokenResponse> refresh(@RequestBody AiRefreshRequest request) {
        return ResponseEntity.ok(aiAuthSessionService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/revoke")
    @Operation(summary = "Revoke AI session", description = "Revoke CLI/MCP session tokens")
    public ResponseEntity<Void> revoke(@RequestBody AiRevokeRequest request) {
        aiAuthSessionService.revoke(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    private MerchantInfo currentMerchant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authService.getMerchantByEmail(authentication.getName());
    }
}
