package com.fusionxpay.ai.cli.command;

import com.fusionxpay.ai.cli.command.support.AbstractCliLeafCommand;
import com.fusionxpay.ai.cli.service.CliSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Component
@RequiredArgsConstructor
@Command(name = "login", description = "Authenticate and persist a CLI session")
public class AuthLoginCommand extends AbstractCliLeafCommand implements Callable<Integer> {

    private final CliSessionService sessionService;

    @Option(names = "--email", description = "Merchant account email (legacy fallback)")
    String email;

    @Option(names = "--password", description = "Merchant account password (legacy fallback)")
    String password;

    @Option(names = "--device", description = "Force device-code browser auth instead of local callback")
    boolean deviceCodeOnly;

    @Override
    public Integer call() {
        CliSessionService.LoginResult result = hasLegacyCredentials()
                ? sessionService.login(email, password)
                : sessionService.loginInteractive(!deviceCodeOnly, message -> out().println(message));
        out().printf("Logged in as %s%n", result.config().getMerchantEmail());
        out().printf("Merchant ID: %s%n", result.config().getMerchantId());
        out().printf("Merchant Name: %s%n", result.config().getMerchantName());
        out().printf("Server: %s%n", result.config().getBaseUrl());
        if (result.expiresInSeconds() != null) {
            out().printf("Token Expires In: %ss%n", result.expiresInSeconds());
        }
        setAuditOutputSummary("merchantId=" + result.config().getMerchantId());
        return 0;
    }

    @Override
    public String auditActionName() {
        return "auth.login";
    }

    @Override
    public String auditInputSummary() {
        return hasLegacyCredentials() ? "email=" + email : "interactive";
    }

    private boolean hasLegacyCredentials() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
