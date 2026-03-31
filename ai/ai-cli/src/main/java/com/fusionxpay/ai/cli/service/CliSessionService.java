package com.fusionxpay.ai.cli.service;

import com.fusionxpay.ai.cli.client.CliGatewayClientProvider;
import com.fusionxpay.ai.cli.config.CliConfigStore;
import com.fusionxpay.ai.cli.config.CliStoredConfig;
import com.fusionxpay.ai.common.dto.auth.GatewayLoginResponse;
import com.fusionxpay.ai.common.dto.auth.GatewayMerchantInfo;
import com.fusionxpay.ai.common.exception.AiAuthenticationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CliSessionService {

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

    public StatusResult status() {
        Optional<CliStoredConfig> optionalConfig = configStore.load();
        if (optionalConfig.isEmpty() || optionalConfig.get().getJwt() == null || optionalConfig.get().getJwt().isBlank()) {
            return new StatusResult(false, null, false, null);
        }

        CliStoredConfig config = optionalConfig.get();
        try {
            GatewayMerchantInfo merchant = gatewayClientProvider.currentClient().getCurrentMerchant(config.getJwt());
            validateMerchantRole(merchant);
            CliStoredConfig updatedConfig = configStore.mutate(existing -> {
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
        configStore.clearSession();
    }

    public ActiveSession requireSession() {
        CliStoredConfig config = configStore.load()
                .filter(value -> value.getJwt() != null && !value.getJwt().isBlank())
                .orElseThrow(() -> new IllegalStateException("Not logged in. Run `fusionx auth login` first."));

        if (config.getMerchantId() == null) {
            throw new IllegalStateException("Saved CLI session is missing merchant identity. Run `fusionx auth login` again.");
        }

        return new ActiveSession(
                gatewayClientProvider.currentClient(),
                config.getJwt(),
                config.getMerchantId(),
                config
        );
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
}
