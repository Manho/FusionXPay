package com.fusionxpay.ai.cli.command;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "auth", description = "Authentication commands")
public class AuthCommand implements Runnable {

    @Override
    public void run() {
    }
}
