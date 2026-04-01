package com.fusionxpay.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fusionx.ai.audit")
public class AiAuditConsumerProperties {

    private String topic = "ai-audit-log";

    private String consumerGroup = "admin-service-ai-audit";

    private boolean consumerEnabled = true;
}
