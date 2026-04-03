package com.fusionxpay.ai.cli.command;

import com.fusionxpay.ai.cli.command.support.AbstractCliLeafCommand;
import com.fusionxpay.ai.cli.command.support.CliRenderSupport;
import com.fusionxpay.ai.cli.service.CliCommandService;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationResponse;
import com.fusionxpay.ai.common.dto.payment.InitiatePaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;

@Component
@RequiredArgsConstructor
@Command(name = "pay", description = "Prepare a payment request and return a confirmation token")
public class PaymentPayCommand extends AbstractCliLeafCommand implements Callable<Integer> {

    private final CliCommandService commandService;

    @Option(names = "--order-id", required = true, description = "Order UUID to pay")
    String orderId;

    @Option(names = "--amount", required = true, description = "Payment amount")
    BigDecimal amount;

    @Option(names = "--currency", required = true, description = "Currency code")
    String currency;

    @Option(names = "--channel", required = true, description = "Payment channel such as STRIPE or PAYPAL")
    String paymentChannel;

    @Option(names = "--description", description = "Optional payment description")
    String description;

    @Option(names = "--return-url", description = "Optional provider return URL")
    String returnUrl;

    @Option(names = "--cancel-url", description = "Optional provider cancel URL")
    String cancelUrl;

    @Option(names = "--success-url", description = "Optional frontend success URL")
    String successUrl;

    @Option(names = "--merchant-reference", description = "Optional merchant reference")
    String merchantReference;

    @Override
    public Integer call() {
        ConfirmationResponse response = commandService.initiatePayment(InitiatePaymentRequest.builder()
                .orderId(UUID.fromString(orderId))
                .amount(amount)
                .currency(currency)
                .paymentChannel(paymentChannel)
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .successUrl(successUrl)
                .merchantReference(merchantReference)
                .build());
        setAuditOutputSummary(CliRenderSupport.renderConfirmation(out(), response));
        return 0;
    }

    @Override
    public String auditActionName() {
        return "payment.pay";
    }

    @Override
    public String auditInputSummary() {
        return "orderId=" + orderId + ", amount=" + amount + ", currency=" + currency + ", channel=" + paymentChannel;
    }
}
