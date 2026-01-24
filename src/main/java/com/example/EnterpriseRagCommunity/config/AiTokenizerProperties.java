package com.example.EnterpriseRagCommunity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.ai.tokenizer")
public class AiTokenizerProperties {
    private String apiKey;
}

