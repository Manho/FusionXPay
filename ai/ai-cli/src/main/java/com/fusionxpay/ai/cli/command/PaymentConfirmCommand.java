package com.fusionxpay.ai.cli.command;

import com.fusionxpay.ai.cli.command.support.AbstractCliLeafCommand;
import com.fusionxpay.ai.cli.command.support.CliRenderSupport;
import com.fusionxpay.ai.cli.service.CliCommandService;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Component
@RequiredArgsConstructor
@Command(name = "confirm", description = "Execute a pending write action by confirmation token")
public class PaymentConfirmCommand extends AbstractCliLeafCommand implements Callable<Integer> {

    private final CliCommandService commandService;

    @Option(names = "--token", required = true, description = "Confirmation token")
    String token;

    @Override
    public Integer call() {
        ConfirmationResponse response = commandService.confirmAction(token);
        setAuditOutputSummary(CliRenderSupport.renderConfirmation(out(), response));
        return 0;
    }

    @Override
    public String auditActionName() {
        return "payment.confirm";
    }

    @Override
    public String auditInputSummary() {
        return "token=" + token;
    }
}
