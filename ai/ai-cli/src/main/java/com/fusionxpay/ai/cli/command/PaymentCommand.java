package com.fusionxpay.ai.cli.command;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "payment", description = "Payment commands")
public class PaymentCommand implements Runnable {

    @Override
    public void run() {
    }
}
