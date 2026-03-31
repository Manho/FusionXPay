package com.fusionxpay.ai.cli.command;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Component
@Command(
        name = "fusionx",
        mixinStandardHelpOptions = true,
        description = "FusionXPay AI CLI",
        exitCodeOnInvalidInput = 2,
        subcommands = {
                AuthCommand.class,
                PaymentCommand.class,
                OrderCommand.class
        }
)
public class RootCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
