package com.fusionxpay.ai.cli.command;

import com.fusionxpay.ai.cli.command.support.AbstractCliLeafCommand;
import com.fusionxpay.ai.cli.service.CliSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Component
@RequiredArgsConstructor
@Command(name = "status", description = "Show saved CLI authentication status")
public class AuthStatusCommand extends AbstractCliLeafCommand implements Callable<Integer> {

    private final CliSessionService sessionService;

    @Override
    public Integer call() {
        CliSessionService.StatusResult status = sessionService.status();
        if (!status.configured()) {
            out().println("Not logged in");
            setAuditOutputSummary("configured=false");
            return 0;
        }

        out().printf("Configured: yes%n");
        out().printf("Server: %s%n", status.config().getBaseUrl());
        out().printf("Merchant Email: %s%n", status.config().getMerchantEmail());
        out().printf("Merchant ID: %s%n", status.config().getMerchantId());
        out().printf("Merchant Name: %s%n", status.config().getMerchantName());
        out().printf("Authenticated: %s%n", status.authenticated() ? "yes" : "no");
        if (!status.authenticated() && status.errorMessage() != null) {
            out().printf("Error: %s%n", status.errorMessage());
        }
        setAuditOutputSummary("configured=true, authenticated=" + status.authenticated());
        return 0;
    }

    @Override
    public String auditActionName() {
        return "auth.status";
    }

    @Override
    public String auditInputSummary() {
        return "status";
    }
}
