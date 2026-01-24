package com.example.EnterpriseRagCommunity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.opensearch.platform")
public class OpenSearchPlatformProperties {
    private String host;
    private String apiKey;
    private String workspaceName = "default";
    private String serviceId = "ops-qwen-turbo";
    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 10_000;
}
