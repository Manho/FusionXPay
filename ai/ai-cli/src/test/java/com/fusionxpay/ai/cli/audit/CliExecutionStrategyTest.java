package com.fusionxpay.ai.cli.audit;

import com.fusionxpay.ai.common.audit.AuditRequestMetadata;
import com.fusionxpay.ai.common.audit.ThreadLocalAuditRequestMetadataProvider;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CliExecutionStrategyTest {

    @Test
    void exposesAuditMetadataDuringCommandExecution() {
        ThreadLocalAuditRequestMetadataProvider provider = new ThreadLocalAuditRequestMetadataProvider();
        AtomicReference<AuditRequestMetadata> captured = new AtomicReference<>();
        CliExecutionStrategy strategy = new CliExecutionStrategy(provider);
        CommandLine commandLine = new CommandLine(new SuccessfulCommand(provider, captured));
        commandLine.setExecutionStrategy(strategy);

        int exitCode = commandLine.execute();

        assertThat(exitCode).isEqualTo(0);
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().source()).isEqualTo("CLI-Java");
        assertThat(captured.get().actionName()).isEqualTo("test.ok");
        assertThat(captured.get().correlationId()).isNotBlank();
        assertThat(provider.currentMetadata()).isNull();
    }

    @Test
    void clearsAuditMetadataAfterNonZeroExitCommand() {
        ThreadLocalAuditRequestMetadataProvider provider = new ThreadLocalAuditRequestMetadataProvider();
        CliExecutionStrategy strategy = new CliExecutionStrategy(provider);
        CommandLine commandLine = new CommandLine(new FailingCommand(provider));
        commandLine.setExecutionStrategy(strategy);

        int exitCode = commandLine.execute();

        assertThat(exitCode).isEqualTo(7);
        assertThat(provider.currentMetadata()).isNull();
    }

    @Command(name = "ok")
    static class SuccessfulCommand implements Callable<Integer>, CliAuditableCommand {
        private final ThreadLocalAuditRequestMetadataProvider provider;
        private final AtomicReference<AuditRequestMetadata> captured;

        SuccessfulCommand(ThreadLocalAuditRequestMetadataProvider provider,
                          AtomicReference<AuditRequestMetadata> captured) {
            this.provider = provider;
            this.captured = captured;
        }

        @Override
        public Integer call() {
            captured.set(provider.currentMetadata());
            return 0;
        }

        @Override
        public String auditActionName() {
            return "test.ok";
        }

        @Override
        public String auditInputSummary() {
            return "ok";
        }

        @Override
        public String auditOutputSummary() {
            return "done";
        }
    }

    @Command(name = "fail")
    static class FailingCommand implements Callable<Integer>, CliAuditableCommand {
        private final ThreadLocalAuditRequestMetadataProvider provider;

        FailingCommand(ThreadLocalAuditRequestMetadataProvider provider) {
            this.provider = provider;
        }

        @Override
        public Integer call() {
            assertThat(provider.currentMetadata()).isNotNull();
            return 7;
        }

        @Override
        public String auditActionName() {
            return "test.fail";
        }

        @Override
        public String auditInputSummary() {
            return "fail";
        }

        @Override
        public String auditOutputSummary() {
            return "not-ok";
        }
    }
}
