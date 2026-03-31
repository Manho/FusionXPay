package com.fusionxpay.ai.cli.command;

import com.fusionxpay.ai.cli.command.support.AbstractCliLeafCommand;
import com.fusionxpay.ai.cli.command.support.CliRenderSupport;
import com.fusionxpay.ai.cli.service.CliCommandService;
import com.fusionxpay.ai.common.audit.AuditStatus;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationResponse;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationStatus;
import com.fusionxpay.ai.common.dto.payment.RefundPaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.math.BigDecimal;
import java.util.concurrent.Callable;

@Component
@RequiredArgsConstructor
@Command(name = "refund", description = "Prepare a refund request and return a confirmation token")
public class PaymentRefundCommand extends AbstractCliLeafCommand implements Callable<Integer> {

    private final CliCommandService commandService;

    @Option(names = "--transaction-id", required = true, description = "Payment transaction UUID")
    String transactionId;

    @Option(names = "--amount", description = "Optional refund amount")
    BigDecimal amount;

    @Option(names = "--reason", description = "Optional refund reason")
    String reason;

    @Option(names = "--currency", description = "Optional currency code")
    String currency;

    @Option(names = "--capture-id", description = "Optional PayPal capture ID")
    String captureId;

    @Override
    public Integer call() {
        ConfirmationResponse response = commandService.refundPayment(RefundPaymentRequest.builder()
                .transactionId(transactionId)
                .amount(amount)
                .reason(reason)
                .currency(currency)
                .captureId(captureId)
                .build());
        setAuditOutputSummary(CliRenderSupport.renderConfirmation(out(), response));
        if (response.getStatus() == ConfirmationStatus.CONFIRMATION_REQUIRED) {
            setAuditStatus(AuditStatus.CONFIRMATION_REQUIRED);
        }
        return 0;
    }

    @Override
    public String auditActionName() {
        return "payment.refund";
    }

    @Override
    public String auditInputSummary() {
        return "transactionId=" + transactionId + ", amount=" + value(amount) + ", currency=" + value(currency);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
