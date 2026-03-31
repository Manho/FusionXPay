package com.fusionxpay.ai.cli.command;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "fusionx", mixinStandardHelpOptions = true, description = "FusionXPay AI CLI")
public class RootCommand implements Runnable {

    @Override
    public void run() {
        // Root command intentionally prints usage until Stage 6 wires the real subcommands.
    }
}
