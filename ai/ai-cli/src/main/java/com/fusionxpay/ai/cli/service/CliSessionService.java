package com.fusionxpay.ai.cli.service;

import com.fusionxpay.ai.cli.client.CliGatewayClientProvider;
import com.fusionxpay.ai.cli.config.CliConfigStore;
import com.fusionxpay.ai.cli.config.CliStoredConfig;
import com.fusionxpay.ai.common.dto.auth.GatewayLoginResponse;
import com.fusionxpay.ai.common.dto.auth.GatewayMerchantInfo;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthClientType;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthFlowMode;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthPollStatus;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthorizeRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthorizeResponse;
import com.fusionxpay.ai.common.dto.auth.ai.AiPollRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiRefreshRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiRevokeRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiTokenExchangeRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiTokenResponse;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthGrantType;
import com.fusionxpay.ai.common.exception.AiAuthenticationException;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@Service
@Slf4j
@RequiredArgsConstructor
public class CliSessionService {

    private static final String CLI_AUDIENCE = "ai-cli";
    private static final Duration CALLBACK_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration DEVICE_TIMEOUT = Duration.ofMinutes(3);

    private final CliGatewayClientProvider gatewayClientProvider;
    private final CliConfigStore configStore;

    public LoginResult login(String email, String password) {
        URI baseUrl = gatewayClientProvider.resolveBaseUrl();
        GatewayLoginResponse response = gatewayClientProvider.clientFor(baseUrl).login(email, password);
        GatewayMerchantInfo merchant = response.getMerchant();
        if (merchant == null) {
            merchant = gatewayClientProvider.clientFor(baseUrl).getCurrentMerchant(response.getToken());
        }
        validateMerchantRole(merchant);
        GatewayMerchantInfo resolvedMerchant = merchant;

        CliStoredConfig config = configStore.mutate(existing -> {
            existing.setBaseUrl(baseUrl.toString());
            existing.setJwt(response.getToken());
            existing.setRefreshToken(null);
            existing.setAudience(null);
            existing.setTokenType(null);
            existing.setMerchantEmail(resolvedMerchant.getEmail() == null || resolvedMerchant.getEmail().isBlank()
                    ? email
                    : resolvedMerchant.getEmail());
            existing.setMerchantId(resolvedMerchant.getId());
            existing.setMerchantName(resolvedMerchant.getMerchantName());
            existing.setMerchantRole(resolvedMerchant.getRole());
            return existing;
        });

        return new LoginResult(config, response.getExpiresIn());
    }

    public LoginResult loginInteractive(boolean preferCallback, Consumer<String> updates) {
        URI baseUrl = gatewayClientProvider.resolveBaseUrl();
        var gatewayClient = gatewayClientProvider.clientFor(baseUrl);

        if (preferCallback) {
            try {
                return loginWithCallback(baseUrl, gatewayClient, updates);
            } catch (RuntimeException ex) {
                updates.accept("Local callback flow unavailable. Falling back to device code.");
                log.warn("CLI callback auth flow failed, falling back to device code", ex);
            }
        }

        return loginWithDeviceCode(baseUrl, gatewayClient, updates);
    }

    public StatusResult status() {
        Optional<CliStoredConfig> optionalConfig = configStore.load();
        if (optionalConfig.isEmpty() || optionalConfig.get().getJwt() == null || optionalConfig.get().getJwt().isBlank()) {
            return new StatusResult(false, null, false, null);
        }

        CliStoredConfig config = optionalConfig.get();
        try {
            CliStoredConfig ensuredConfig = ensureFreshSession(config);
            GatewayMerchantInfo merchant = gatewayClientProvider.currentClient().getCurrentMerchant(ensuredConfig.getJwt());
            validateMerchantRole(merchant);
            CliStoredConfig updatedConfig = configStore.mutate(existing -> {
                existing.setJwt(ensuredConfig.getJwt());
                existing.setRefreshToken(ensuredConfig.getRefreshToken());
                existing.setAudience(ensuredConfig.getAudience());
                existing.setTokenType(ensuredConfig.getTokenType());
                existing.setMerchantEmail(merchant.getEmail());
                existing.setMerchantId(merchant.getId());
                existing.setMerchantName(merchant.getMerchantName());
                existing.setMerchantRole(merchant.getRole());
                return existing;
            });
            return new StatusResult(true, updatedConfig, true, null);
        } catch (RuntimeException ex) {
            return new StatusResult(true, config, false, ex.getMessage());
        }
    }

    public void logout() {
        configStore.load()
                .map(CliStoredConfig::getRefreshToken)
                .filter(value -> value != null && !value.isBlank())
                .ifPresent(refreshToken -> {
                    try {
                        gatewayClientProvider.currentClient().revokeAiSession(new AiRevokeRequest(refreshToken));
                    } catch (RuntimeException ex) {
                        log.warn("Failed to revoke CLI session before logout", ex);
                    }
                });
        configStore.clearSession();
    }

    public ActiveSession requireSession() {
        CliStoredConfig config = configStore.load()
                .filter(value -> value.getJwt() != null && !value.getJwt().isBlank())
                .orElseThrow(() -> new IllegalStateException("Not logged in. Run `fusionx auth login` first."));
        CliStoredConfig ensuredConfig = ensureFreshSession(config);

        if (ensuredConfig.getMerchantId() == null) {
            throw new IllegalStateException("Saved CLI session is missing merchant identity. Run `fusionx auth login` again.");
        }

        GatewayMerchantInfo merchant = gatewayClientProvider.currentClient().getCurrentMerchant(ensuredConfig.getJwt());
        validateMerchantRole(merchant);

        return new ActiveSession(
                gatewayClientProvider.currentClient(),
                ensuredConfig.getJwt(),
                ensuredConfig.getMerchantId(),
                ensuredConfig
        );
    }

    private LoginResult loginWithCallback(URI baseUrl,
                                          com.fusionxpay.ai.common.client.GatewayClient gatewayClient,
                                          Consumer<String> updates) {
        PkcePair pkcePair = PkcePair.create();
        try (LocalCallbackServer callbackServer = LocalCallbackServer.start()) {
            AiAuthorizeResponse response = gatewayClient.authorizeAiSession(AiAuthorizeRequest.builder()
                    .clientType(AiAuthClientType.CLI)
                    .audience(CLI_AUDIENCE)
                    .flowMode(AiAuthFlowMode.CALLBACK)
                    .callbackUrl(callbackServer.callbackUrl().toString())
                    .state(callbackServer.state())
                    .codeChallenge(pkcePair.challenge())
                    .codeChallengeMethod("S256")
                    .build());

            updates.accept("Opening your browser to approve this CLI session...");
            openBrowser(response.getAuthorizationUrl(), updates);

            CallbackPayload payload = callbackServer.await(CALLBACK_TIMEOUT);
            if (!callbackServer.state().equals(payload.state())) {
                throw new IllegalStateException("Browser auth callback state mismatch");
            }

            AiTokenResponse tokenResponse = gatewayClient.exchangeAiToken(AiTokenExchangeRequest.builder()
                    .grantType(AiAuthGrantType.AUTHORIZATION_CODE)
                    .code(payload.code())
                    .codeVerifier(pkcePair.verifier())
                    .build());
            return persistTokenResponse(baseUrl, tokenResponse);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to start local callback server", ex);
        }
    }

    private LoginResult loginWithDeviceCode(URI baseUrl,
                                            com.fusionxpay.ai.common.client.GatewayClient gatewayClient,
                                            Consumer<String> updates) {
        AiAuthorizeResponse response = gatewayClient.authorizeAiSession(AiAuthorizeRequest.builder()
                .clientType(AiAuthClientType.CLI)
                .audience(CLI_AUDIENCE)
                .flowMode(AiAuthFlowMode.DEVICE_CODE)
                .build());

        updates.accept("Approve this CLI session in your browser.");
        updates.accept("Verification URL: " + response.getVerificationUrl());
        updates.accept("User code: " + response.getUserCode());
        openBrowser(response.getAuthorizationUrl(), updates);

        long pollInterval = response.getPollIntervalSeconds() == null ? 3L : response.getPollIntervalSeconds();
        Instant deadline = Instant.now().plus(DEVICE_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            AiAuthPollStatus pollStatus = gatewayClient.pollAiSession(new AiPollRequest(response.getDeviceCode())).getStatus();
            if (pollStatus == AiAuthPollStatus.APPROVED) {
                AiTokenResponse tokenResponse = gatewayClient.exchangeAiToken(AiTokenExchangeRequest.builder()
                        .grantType(AiAuthGrantType.DEVICE_CODE)
                        .deviceCode(response.getDeviceCode())
                        .build());
                return persistTokenResponse(baseUrl, tokenResponse);
            }
            if (pollStatus == AiAuthPollStatus.DENIED || pollStatus == AiAuthPollStatus.EXPIRED || pollStatus == AiAuthPollStatus.REVOKED) {
                throw new IllegalStateException("Device authorization failed with status " + pollStatus);
            }
            sleepSeconds(pollInterval);
        }

        throw new IllegalStateException("Timed out waiting for browser approval");
    }

    private CliStoredConfig ensureFreshSession(CliStoredConfig config) {
        try {
            gatewayClientProvider.currentClient().getCurrentMerchant(config.getJwt());
            return config;
        } catch (RuntimeException ex) {
            if (config.getRefreshToken() == null || config.getRefreshToken().isBlank()) {
                throw ex;
            }
            AiTokenResponse refreshed = gatewayClientProvider.currentClient()
                    .refreshAiToken(new AiRefreshRequest(config.getRefreshToken()));
            return configStore.mutate(existing -> {
                existing.setJwt(refreshed.getAccessToken());
                existing.setRefreshToken(refreshed.getRefreshToken());
                existing.setAudience(refreshed.getAudience());
                existing.setTokenType(refreshed.getSessionTokenType());
                if (refreshed.getMerchant() != null) {
                    existing.setMerchantEmail(refreshed.getMerchant().getEmail());
                    existing.setMerchantId(refreshed.getMerchant().getId());
                    existing.setMerchantName(refreshed.getMerchant().getMerchantName());
                    existing.setMerchantRole(refreshed.getMerchant().getRole());
                }
                return existing;
            });
        }
    }

    private LoginResult persistTokenResponse(URI baseUrl, AiTokenResponse tokenResponse) {
        GatewayMerchantInfo merchant = tokenResponse.getMerchant();
        validateMerchantRole(merchant);
        CliStoredConfig config = configStore.mutate(existing -> {
            existing.setBaseUrl(baseUrl.toString());
            existing.setJwt(tokenResponse.getAccessToken());
            existing.setRefreshToken(tokenResponse.getRefreshToken());
            existing.setAudience(tokenResponse.getAudience());
            existing.setTokenType(tokenResponse.getSessionTokenType());
            existing.setMerchantEmail(merchant.getEmail());
            existing.setMerchantId(merchant.getId());
            existing.setMerchantName(merchant.getMerchantName());
            existing.setMerchantRole(merchant.getRole());
            return existing;
        });
        return new LoginResult(config, tokenResponse.getExpiresIn());
    }

    private void openBrowser(String authorizationUrl, Consumer<String> updates) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(authorizationUrl));
            } else {
                updates.accept("Open this URL manually: " + authorizationUrl);
            }
        } catch (Exception ex) {
            updates.accept("Open this URL manually: " + authorizationUrl);
        }
    }

    private void sleepSeconds(long seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for browser approval", ex);
        }
    }

    private void validateMerchantRole(GatewayMerchantInfo merchant) {
        if (merchant == null || merchant.getId() == null) {
            throw new AiAuthenticationException(401, "Unable to resolve merchant identity for CLI session", null, null);
        }
        if (!"MERCHANT".equalsIgnoreCase(merchant.getRole())) {
            throw new AiAuthenticationException(403, "CLI session must use a merchant account", null, null);
        }
    }

    public record LoginResult(CliStoredConfig config, Long expiresInSeconds) {
    }

    public record StatusResult(boolean configured, CliStoredConfig config, boolean authenticated, String errorMessage) {
    }

    public record ActiveSession(
            com.fusionxpay.ai.common.client.GatewayClient gatewayClient,
            String token,
            Long merchantId,
            CliStoredConfig config
    ) {
    }

    private record CallbackPayload(String code, String state) {
    }

    private record PkcePair(String verifier, String challenge) {
        private static PkcePair create() {
            String verifier = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(UUID.randomUUID().toString().repeat(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new PkcePair(verifier, sha256(verifier));
        }

        private static String sha256(String value) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 unavailable", ex);
            }
        }
    }

    private static final class LocalCallbackServer implements AutoCloseable {
        private final HttpServer server;
        private final String state;
        private final CompletableFuture<CallbackPayload> callbackFuture;

        private LocalCallbackServer(HttpServer server, String state, CompletableFuture<CallbackPayload> callbackFuture) {
            this.server = server;
            this.state = state;
            this.callbackFuture = callbackFuture;
        }

        static LocalCallbackServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            String state = UUID.randomUUID().toString();
            CompletableFuture<CallbackPayload> callbackFuture = new CompletableFuture<>();
            server.createContext("/callback", exchange -> handleCallback(exchange, callbackFuture));
            server.start();
            return new LocalCallbackServer(server, state, callbackFuture);
        }

        URI callbackUrl() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/callback");
        }

        String state() {
            return state;
        }

        CallbackPayload await(Duration timeout) {
            try {
                return callbackFuture.get(timeout.toSeconds(), TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException("Timed out waiting for CLI browser callback", ex);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void handleCallback(HttpExchange exchange, CompletableFuture<CallbackPayload> callbackFuture) throws IOException {
            String query = exchange.getRequestURI().getRawQuery();
            Map<String, String> params = parseQuery(query);
            String code = params.get("code");
            String state = params.get("state");

            byte[] payload = (code == null || code.isBlank()
                    ? "Authorization failed. Return to your terminal."
                    : "Authorization received. You can return to your terminal.")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();

            if (code != null && !code.isBlank()) {
                callbackFuture.complete(new CallbackPayload(code, state));
            }
        }

        private static Map<String, String> parseQuery(String query) {
            Map<String, String> values = new java.util.HashMap<>();
            if (query == null || query.isBlank()) {
                return values;
            }
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    values.put(parts[0], java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return values;
        }
    }
}
