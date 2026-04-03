package com.fusionxpay.ai.cli.audit;

public interface CliAuditableCommand {

    String auditActionName();

    String auditInputSummary();

    String auditOutputSummary();
}
