package com.fusionxpay.api.gateway.controller;

import com.fusionxpay.api.gateway.dto.AuthRequest;
import com.fusionxpay.api.gateway.dto.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Authentication", description = "Endpoints for user registration and login")
@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {

    private static final Map<String, String> DEPRECATED_BODY = Map.of(
            "code", "DEPRECATED_ENDPOINT",
            "message", "This endpoint has been removed. Use /api/v1/admin/auth/* instead."
    );

    @Operation(summary = "Deprecated register endpoint")
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody AuthRequest request) {
        log.warn("Deprecated endpoint called: /api/v1/auth/register username={}", request.getUsername());
        return ResponseEntity.status(HttpStatus.GONE).body(DEPRECATED_BODY);
    }

    @Operation(summary = "Deprecated login endpoint")
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody AuthRequest request) {
        log.warn("Deprecated endpoint called: /api/v1/auth/login username={}", request.getUsername());
        return ResponseEntity.status(HttpStatus.GONE).body(DEPRECATED_BODY);
    }
}
