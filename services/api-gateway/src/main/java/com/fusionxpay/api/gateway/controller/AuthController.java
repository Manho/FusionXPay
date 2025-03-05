package com.fusionxpay.api.gateway.controller;

import com.fusionxpay.api.gateway.dto.AuthRequest;
import com.fusionxpay.api.gateway.dto.AuthResponse;
import com.fusionxpay.api.gateway.service.UserService;
import com.fusionxpay.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Endpoints for user registration and login")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    @Operation(summary = "Register a new user", description = "Creates a new user account and returns an authentication response with API key")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody AuthRequest request) {
        try {
            AuthResponse response = userService.register(request);
            log.info("User [{}] registered successfully", response.getUsername());
            ApiResponse<AuthResponse> apiResponse = ApiResponse.<AuthResponse>builder()
                    .code(200)
                    .message("Success")
                    .data(response)
                    .build();
            return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
        } catch (Exception ex) {
            log.error("Registration failed: {}", ex.getMessage(), ex);
            ApiResponse<AuthResponse> apiResponse = ApiResponse.<AuthResponse>builder()
                    .code(HttpStatus.BAD_REQUEST.value())
                    .message(ex.getMessage())
                    .build();
            return ResponseEntity.badRequest().body(apiResponse);
        }
    }

    @Operation(summary = "User login", description = "Authenticates an existing user and returns their API key")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        try {
            AuthResponse response = userService.login(request);
            log.info("User [{}] logged in successfully", response.getUsername());
            ApiResponse<AuthResponse> apiResponse = ApiResponse.<AuthResponse>builder()
                    .code(200)
                    .message("Success")
                    .data(response)
                    .build();
            return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
        } catch (Exception ex) {
            log.error("Login failed: {}", ex.getMessage(), ex);
            ApiResponse<AuthResponse> apiResponse = ApiResponse.<AuthResponse>builder()
                    .code(HttpStatus.BAD_REQUEST.value())
                    .message(ex.getMessage())
                    .build();
            return ResponseEntity.badRequest().body(apiResponse);
        }
    }
}