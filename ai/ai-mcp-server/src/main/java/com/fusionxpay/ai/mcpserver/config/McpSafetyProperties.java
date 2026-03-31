package com.fusionxpay.ai.mcpserver.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "fusionx.ai.mcp.safety")
public class McpSafetyProperties {

    private int maxInputLength = 4000;

    private double suspiciousCharacterRatio = 0.35d;

    private int maxSummaryLength = 600;

    private List<String> blockedPatterns = new ArrayList<>(List.of(
            "(?i)ignore\\s+previous\\s+instructions",
            "(?i)system\\s+prompt",
            "(?i)developer\\s+message",
            "(?i)<script",
            "(?i)rm\\s+-rf",
            "(?i)drop\\s+table",
            "(?i)curl\\s+.+\\|\\s*sh"
    ));

    private List<String> redactionPatterns = new ArrayList<>(List.of(
            "(?i)bearer\\s+[A-Za-z0-9._-]+",
            "(?i)\\b(?:sk|pk)_(?:test|live)_[A-Za-z0-9]+\\b",
            "(?i)\\bwhsec_[A-Za-z0-9]+\\b",
            "(?i)paypal[_-]?client[_-]?secret\\s*[:=]\\s*[^\\s,]+",
            "(?i)jwt[_-]?secret\\s*[:=]\\s*[^\\s,]+"
    ));
}
