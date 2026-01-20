package com.fusionxpay.payment.dto.paypal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for PayPal OAuth 2.0 token endpoint.
 * Contains access token and related metadata.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayPalTokenResponse {

    /**
     * The access token issued by PayPal.
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * The type of token (typically "Bearer").
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * The application ID associated with the token.
     */
    @JsonProperty("app_id")
    private String appId;

    /**
     * Token expiration time in seconds.
     */
    @JsonProperty("expires_in")
    private Long expiresIn;

    /**
     * The scope of access granted.
     */
    private String scope;

    /**
     * A unique nonce value.
     */
    private String nonce;
}
