package com.fusionxpay.ai.cli.command;

import com.fusionxpay.ai.cli.command.support.AbstractCliLeafCommand;
import com.fusionxpay.ai.cli.command.support.CliRenderSupport;
import com.fusionxpay.ai.cli.service.CliCommandService;
import com.fusionxpay.ai.common.dto.payment.PaymentRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Component
@RequiredArgsConstructor
@Command(name = "query", description = "Query a payment by transaction ID or order ID")
public class PaymentQueryCommand extends AbstractCliLeafCommand implements Callable<Integer> {

    private final CliCommandService commandService;

    @Option(names = "--transaction-id", description = "Payment transaction UUID")
    String transactionId;

    @Option(names = "--order-id", description = "Order UUID when transaction ID is unknown")
    String orderId;

    @Override
    public Integer call() {
        PaymentRecord payment = commandService.queryPayment(transactionId, orderId);
        setAuditOutputSummary(CliRenderSupport.renderPayment(out(), payment));
        return 0;
    }

    @Override
    public String auditActionName() {
        return "payment.query";
    }

    @Override
    public String auditInputSummary() {
        return "transactionId=" + value(transactionId) + ", orderId=" + value(orderId);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
