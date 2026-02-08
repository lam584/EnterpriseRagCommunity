package com.example.EnterpriseRagCommunity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.ai.queue")
public class LlmQueueProperties {
    private int maxConcurrent = 4;
    private int maxQueueSize = 5000;
    private int keepCompleted = 2000;
    private int historyKeepDays = 30;
}
