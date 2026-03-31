package com.fusionxpay.ai.cli.audit;

import com.fusionxpay.ai.cli.config.CliConfigStore;
import com.fusionxpay.ai.cli.config.CliStoredConfig;
import com.fusionxpay.ai.common.audit.AuditEvent;
import com.fusionxpay.ai.common.audit.AuditEventPublisher;
import com.fusionxpay.ai.common.audit.AuditStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.Optional;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CliExecutionStrategyTest {

    @Test
    void publishesSuccessAuditForSuccessfulCommand() {
        AuditEventPublisher publisher = mock(AuditEventPublisher.class);
        CliConfigStore configStore = mock(CliConfigStore.class);
        when(configStore.load()).thenReturn(Optional.of(CliStoredConfig.builder().merchantId(88L).build()));

        CliExecutionStrategy strategy = new CliExecutionStrategy(publisher, configStore);
        CommandLine commandLine = new CommandLine(new SuccessfulCommand());
        commandLine.setExecutionStrategy(strategy);

        int exitCode = commandLine.execute();

        assertThat(exitCode).isEqualTo(0);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AuditStatus.SUCCESS);
        assertThat(captor.getValue().getMerchantId()).isEqualTo(88L);
    }

    @Test
    void publishesFailureAuditForNonZeroExitCommand() {
        AuditEventPublisher publisher = mock(AuditEventPublisher.class);
        CliConfigStore configStore = mock(CliConfigStore.class);
        when(configStore.load()).thenReturn(Optional.empty());

        CliExecutionStrategy strategy = new CliExecutionStrategy(publisher, configStore);
        CommandLine commandLine = new CommandLine(new FailingCommand());
        commandLine.setExecutionStrategy(strategy);

        int exitCode = commandLine.execute();

        assertThat(exitCode).isEqualTo(7);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AuditStatus.FAILURE);
        assertThat(captor.getValue().getOutputSummary()).contains("exitCode=7");
    }

    @Command(name = "ok")
    static class SuccessfulCommand implements Callable<Integer>, CliAuditableCommand {
        @Override
        public Integer call() {
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
        @Override
        public Integer call() {
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
