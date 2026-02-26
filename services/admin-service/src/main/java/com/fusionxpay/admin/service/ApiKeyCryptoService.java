package com.fusionxpay.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API key crypto helper (hash + reversible encryption).
 */
@Service
public class ApiKeyCryptoService {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SecretKeySpec secretKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyCryptoService(@Value("${app.api-key.encryption-secret:fusionxpay-api-key-encryption-secret-32-char}") String encryptionSecret) {
        if (encryptionSecret == null || encryptionSecret.length() < 32) {
            throw new IllegalStateException("app.api-key.encryption-secret must be at least 32 characters");
        }
        byte[] keyBytes = encryptionSecret.substring(0, 32).getBytes(StandardCharsets.UTF_8);
        this.secretKeySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash API key", ex);
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            String ivEncoded = Base64.getEncoder().encodeToString(iv);
            String encryptedEncoded = Base64.getEncoder().encodeToString(encrypted);
            return ivEncoded + "." + encryptedEncoded;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt API key", ex);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            String[] parts = ciphertext.split("\\.", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted payload");
            }

            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt API key", ex);
        }
    }
}
