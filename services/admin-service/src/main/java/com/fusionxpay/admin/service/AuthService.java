package com.fusionxpay.admin.service;

import com.fusionxpay.admin.dto.LoginRequest;
import com.fusionxpay.admin.dto.LoginResponse;
import com.fusionxpay.admin.dto.MerchantInfo;
import com.fusionxpay.admin.dto.RegisterRequest;
import com.fusionxpay.admin.exception.ConflictException;
import com.fusionxpay.admin.model.Merchant;
import com.fusionxpay.admin.model.MerchantRole;
import com.fusionxpay.admin.model.MerchantStatus;
import com.fusionxpay.admin.repository.MerchantRepository;
import com.fusionxpay.admin.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

/**
 * Authentication Service - handles login and token generation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final MerchantRepository merchantRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyService apiKeyService;

    /**
     * Authenticate merchant and generate JWT token
     */
    public LoginResponse login(LoginRequest request) {
        try {
            // Authenticate with Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            // Get merchant from database
            Merchant merchant = merchantRepository.findByEmailAndStatus(request.getEmail(), MerchantStatus.ACTIVE)
                    .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

            // Generate JWT token
            String token = jwtTokenProvider.generateToken(
                    merchant.getId(),
                    merchant.getEmail(),
                    merchant.getRole().name()
            );

            log.info("Merchant logged in successfully: {}", merchant.getEmail());

            return LoginResponse.of(
                    token,
                    jwtTokenProvider.getExpirationInSeconds(),
                    MerchantInfo.fromEntity(merchant)
            );

        } catch (AuthenticationException ex) {
            log.warn("Login failed for email: {}", request.getEmail());
            throw new BadCredentialsException("Invalid email or password");
        }
    }

    /**
     * Get merchant info by ID
     */
    public MerchantInfo getMerchantById(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));
        return MerchantInfo.fromEntity(merchant);
    }

    /**
     * Get merchant info by email
     */
    public MerchantInfo getMerchantByEmail(String email) {
        Merchant merchant = merchantRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));
        return MerchantInfo.fromEntity(merchant);
    }

    /**
     * Register a new merchant
     */
    public LoginResponse register(RegisterRequest request) {
        if (merchantRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        String merchantCode = request.getMerchantCode();
        if (merchantCode == null || merchantCode.isBlank()) {
            merchantCode = generateMerchantCode();
        } else if (merchantRepository.existsByMerchantCode(merchantCode)) {
            throw new ConflictException("Merchant code already exists");
        }

        Merchant merchant = Merchant.builder()
                .merchantName(request.getMerchantName())
                .email(request.getEmail())
                .merchantCode(merchantCode)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(MerchantRole.MERCHANT)
                .status(MerchantStatus.ACTIVE)
                .build();

        merchant = merchantRepository.save(merchant);
        String initialApiKey = apiKeyService.createInitialApiKey(merchant.getId(), merchant.getId(), null, null);

        String token = jwtTokenProvider.generateToken(
                merchant.getId(),
                merchant.getEmail(),
                merchant.getRole().name()
        );

        return LoginResponse.of(
                token,
                jwtTokenProvider.getExpirationInSeconds(),
                MerchantInfo.fromEntity(merchant),
                initialApiKey
        );
    }

    private String generateMerchantCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "MCH" + UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase(Locale.ROOT);
            if (!merchantRepository.existsByMerchantCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique merchant code");
    }
}
