package com.fusionxpay.ai.cli.command;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "order", description = "Order commands")
public class OrderCommand implements Runnable {

    @Override
    public void run() {
    }
}
