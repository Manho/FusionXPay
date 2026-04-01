package com.fusionxpay.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fusionx.ai.audit.consumer")
public class AiAuditConsumerProperties {

    private String group = "admin-service-ai-audit";

    private boolean enabled = true;
}
