package com.fusionxpay.admin.service;

import com.fusionxpay.admin.dto.LoginRequest;
import com.fusionxpay.admin.dto.LoginResponse;
import com.fusionxpay.admin.dto.MerchantInfo;
import com.fusionxpay.admin.model.Merchant;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

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
    public LoginResponse register(com.fusionxpay.admin.dto.RegisterRequest request) {
        if (merchantRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        if (merchantRepository.findByMerchantCode(request.getMerchantCode()).isPresent()) {
            throw new RuntimeException("Merchant code already exists");
        }

        // Create new merchant
        Merchant merchant = Merchant.builder()
                .merchantName(request.getMerchantName())
                .email(request.getEmail())
                .merchantCode(request.getMerchantCode())
                .passwordHash(new BCryptPasswordEncoder().encode(request.getPassword()))
                .role(com.fusionxpay.admin.model.MerchantRole.MERCHANT)
                .status(MerchantStatus.ACTIVE)
                .build();

        merchant = merchantRepository.save(merchant);

        // Generate token
        String token = jwtTokenProvider.generateToken(
                merchant.getId(),
                merchant.getEmail(),
                merchant.getRole().name()
        );

        return LoginResponse.of(
                token,
                jwtTokenProvider.getExpirationInSeconds(),
                MerchantInfo.fromEntity(merchant)
        );
    }
}
