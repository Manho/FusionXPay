package com.fusionxpay.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.payment.dto.paypal.PayPalTokenResponse;
import com.fusionxpay.payment.dto.paypal.PayPalWebhookHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing PayPal OAuth 2.0 authentication.
 * Handles access token retrieval, caching, and automatic refresh.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class PayPalAuthService {

    private static final String TOKEN_CACHE_KEY = "paypal:access_token";
    private static final long TOKEN_CACHE_TTL_HOURS = 8; // PayPal tokens expire in ~9 hours

    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate;

    @Value("${payment.providers.paypal.client-id}")
    private String clientId;

    @Value("${payment.providers.paypal.client-secret}")
    private String clientSecret;

    @Value("${payment.providers.paypal.base-url}")
    private String baseUrl;

    public PayPalAuthService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        log.info("PayPal Auth Service initialized with base URL: {}", baseUrl);
    }

    /**
     * Retrieves a valid access token for PayPal API calls.
     * First checks Redis cache, then requests a new token if not found or expired.
     *
     * @return valid access token
     * @throws PayPalAuthException if authentication fails
     */
    public String getAccessToken() {
        // Check cache first to avoid unnecessary API calls
        String cachedToken = redisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
        if (cachedToken != null && !cachedToken.isEmpty()) {
            log.debug("Using cached PayPal access token");
            return cachedToken;
        }

        // Request new token from PayPal
        log.info("Requesting new PayPal access token");
        return requestNewToken();
    }

    /**
     * Forces a refresh of the access token, bypassing the cache.
     *
     * @return new access token
     * @throws PayPalAuthException if authentication fails
     */
    public String refreshAccessToken() {
        log.info("Forcing refresh of PayPal access token");
        // Clear cached token
        redisTemplate.delete(TOKEN_CACHE_KEY);
        return requestNewToken();
    }

    /**
     * Requests a new access token from PayPal OAuth 2.0 endpoint.
     *
     * @return new access token
     * @throws PayPalAuthException if the request fails
     */
    private String requestNewToken() {
        try {
            // Create Basic Auth credentials
            String credentials = clientId + ":" + clientSecret;
            String encodedCredentials = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            // Build request headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "Basic " + encodedCredentials);

            // Build request body
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            // Call PayPal OAuth endpoint
            ResponseEntity<PayPalTokenResponse> responseEntity = restTemplate.exchange(
                    baseUrl + "/v1/oauth2/token",
                    HttpMethod.POST,
                    request,
                    PayPalTokenResponse.class
            );

            PayPalTokenResponse response = responseEntity.getBody();
            if (response == null || response.getAccessToken() == null) {
                throw new PayPalAuthException("Failed to obtain access token: empty response");
            }

            String accessToken = response.getAccessToken();

            // Cache the token with TTL slightly less than actual expiration
            long ttlSeconds = response.getExpiresIn() != null
                    ? response.getExpiresIn() - 300 // 5 minutes buffer
                    : TimeUnit.HOURS.toSeconds(TOKEN_CACHE_TTL_HOURS);

            redisTemplate.opsForValue().set(
                    TOKEN_CACHE_KEY,
                    accessToken,
                    Duration.ofSeconds(ttlSeconds)
            );

            log.info("Successfully obtained and cached PayPal access token (expires in {} seconds)", ttlSeconds);
            return accessToken;

        } catch (RestClientException e) {
            log.error("PayPal OAuth request failed: {}", e.getMessage(), e);
            throw new PayPalAuthException("Failed to obtain access token: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during PayPal OAuth request: {}", e.getMessage(), e);
            throw new PayPalAuthException("Failed to obtain access token: " + e.getMessage(), e);
        }
    }

    /**
     * Clears the cached access token.
     * Useful for testing or when token needs to be invalidated.
     */
    public void clearCachedToken() {
        redisTemplate.delete(TOKEN_CACHE_KEY);
        log.info("Cleared cached PayPal access token");
    }

    /**
     * Returns the configured PayPal API base URL.
     *
     * @return base URL for PayPal API
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Verifies the signature of a PayPal webhook notification.
     * Calls PayPal's webhook verification API to ensure the webhook is authentic.
     *
     * @param headers the webhook headers containing signature information
     * @param webhookId the configured webhook ID
     * @param payload the raw webhook payload
     * @return true if signature is valid, false otherwise
     */
    public boolean verifyWebhookSignature(PayPalWebhookHeaders headers, String webhookId, String payload) {
        if (headers == null || !headers.isComplete()) {
            log.error("PayPal webhook verification failed: incomplete headers");
            return false;
        }

        try {
            String accessToken = getAccessToken();

            // Build verification request body
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode webhookEvent = objectMapper.readTree(payload);

            String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                    "auth_algo", headers.getAuthAlgo(),
                    "cert_url", headers.getCertUrl(),
                    "transmission_id", headers.getTransmissionId(),
                    "transmission_sig", headers.getTransmissionSig(),
                    "transmission_time", headers.getTransmissionTime(),
                    "webhook_id", webhookId,
                    "webhook_event", webhookEvent
                )
            );

            // Build request headers
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.setBearerAuth(accessToken);

            HttpEntity<String> request = new HttpEntity<>(requestBody, httpHeaders);

            // Call PayPal webhook verification endpoint
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    baseUrl + "/v1/notifications/verify-webhook-signature",
                    HttpMethod.POST,
                    request,
                    JsonNode.class
            );

            JsonNode responseBody = response.getBody();
            if (responseBody == null) {
                log.error("PayPal webhook verification returned null response");
                return false;
            }

            String verificationStatus = responseBody.path("verification_status").asText();
            boolean isValid = "SUCCESS".equals(verificationStatus);

            if (isValid) {
                log.debug("PayPal webhook signature verified successfully");
            } else {
                log.warn("PayPal webhook signature verification failed: status={}", verificationStatus);
            }

            return isValid;

        } catch (RestClientException e) {
            log.error("PayPal webhook verification API call failed: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Error during PayPal webhook verification: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Exception thrown when PayPal authentication fails.
     */
    public static class PayPalAuthException extends RuntimeException {
        public PayPalAuthException(String message) {
            super(message);
        }

        public PayPalAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
