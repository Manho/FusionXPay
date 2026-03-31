package com.fusionxpay.ai.cli.command;

import com.fusionxpay.ai.cli.command.support.AbstractCliLeafCommand;
import com.fusionxpay.ai.cli.service.CliSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Component
@RequiredArgsConstructor
@Command(name = "logout", description = "Clear the saved CLI session")
public class AuthLogoutCommand extends AbstractCliLeafCommand implements Callable<Integer> {

    private final CliSessionService sessionService;

    @Override
    public Integer call() {
        sessionService.logout();
        out().println("Logged out");
        setAuditOutputSummary("loggedOut=true");
        return 0;
    }

    @Override
    public String auditActionName() {
        return "auth.logout";
    }

    @Override
    public String auditInputSummary() {
        return "logout";
    }
}
