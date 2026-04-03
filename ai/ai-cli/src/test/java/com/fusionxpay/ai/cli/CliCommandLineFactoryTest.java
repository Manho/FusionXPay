package com.fusionxpay.ai.cli;

import com.fusionxpay.ai.cli.command.AuthLoginCommand;
import com.fusionxpay.ai.cli.command.PaymentConfirmCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "fusionx.ai.cli.runner-enabled=false"
})
class CliCommandLineFactoryTest {

    @Autowired
    private CliCommandLineFactory cliCommandLineFactory;

    @Test
    void parsesAuthLoginCommandTree() {
        CommandLine commandLine = cliCommandLineFactory.create();
        CommandLine.ParseResult parseResult = commandLine.parseArgs("auth", "login", "--email", "merchant@example.com", "--password", "secret");
        Object command = parseResult.asCommandLineList().get(parseResult.asCommandLineList().size() - 1).getCommand();
        assertThat(command).isInstanceOf(AuthLoginCommand.class);
    }

    @Test
    void parsesPaymentConfirmCommandTree() {
        CommandLine commandLine = cliCommandLineFactory.create();
        CommandLine.ParseResult parseResult = commandLine.parseArgs("payment", "confirm", "--token", "abc");
        Object command = parseResult.asCommandLineList().get(parseResult.asCommandLineList().size() - 1).getCommand();
        assertThat(command).isInstanceOf(PaymentConfirmCommand.class);
    }
}
