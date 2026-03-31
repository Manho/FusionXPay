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

    @Option(names = "--email", required = true, description = "Merchant account email")
    String email;

    @Option(names = "--password", required = true, description = "Merchant account password")
    String password;

    @Override
    public Integer call() {
        CliSessionService.LoginResult result = sessionService.login(email, password);
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
        return "email=" + email;
    }
}
