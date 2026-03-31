package com.fusionxpay.ai.cli.command.support;

import com.fusionxpay.ai.cli.audit.CliAuditableCommand;
import com.fusionxpay.ai.common.audit.AuditStatus;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;

public abstract class AbstractCliLeafCommand implements CliAuditableCommand {

    @Spec
    protected CommandSpec spec;

    private String auditOutputSummary = "";
    private AuditStatus auditStatus = AuditStatus.SUCCESS;

    protected PrintWriter out() {
        return spec.commandLine().getOut();
    }

    protected PrintWriter err() {
        return spec.commandLine().getErr();
    }

    protected void setAuditOutputSummary(String auditOutputSummary) {
        this.auditOutputSummary = auditOutputSummary;
    }

    protected void setAuditStatus(AuditStatus auditStatus) {
        this.auditStatus = auditStatus;
    }

    @Override
    public String auditOutputSummary() {
        return auditOutputSummary;
    }

    @Override
    public AuditStatus auditStatus() {
        return auditStatus;
    }
}
