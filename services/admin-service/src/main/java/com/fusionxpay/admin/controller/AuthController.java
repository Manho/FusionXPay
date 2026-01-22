package com.fusionxpay.admin.controller;

import com.fusionxpay.admin.dto.LoginRequest;
import com.fusionxpay.admin.dto.LoginResponse;
import com.fusionxpay.admin.dto.MerchantInfo;
import com.fusionxpay.admin.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller - handles login and user info
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Merchant authentication endpoints")
public class AuthController {

    private final AuthService authService;

    /**
     * Merchant login
     */
    @PostMapping("/login")
    @Operation(summary = "Merchant login", description = "Authenticate merchant and return JWT token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get current authenticated merchant info
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get authenticated merchant information")
    public ResponseEntity<MerchantInfo> getCurrentMerchant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        MerchantInfo merchantInfo = authService.getMerchantByEmail(email);
        return ResponseEntity.ok(merchantInfo);
    }

    /**
     * Merchant registration (Temporary for development)
     */
    @PostMapping("/register")
    @Operation(summary = "Merchant register", description = "Register new merchant")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody com.fusionxpay.admin.dto.RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }
}
