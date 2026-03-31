package com.fusionxpay.ai.mcpserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fusionx.ai.mcp.auth")
public class McpAuthProperties {

    private String jwtToken;
    private String email;
    private String password;
}
