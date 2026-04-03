package com.fusionxpay.ai.cli.command;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "order",
        mixinStandardHelpOptions = true,
        description = "Order commands",
        subcommands = {
                OrderGetCommand.class,
                OrderSearchCommand.class,
                OrderStatusCommand.class
        }
)
public class OrderCommand implements Runnable {

    @Override
    public void run() {
    }
}
