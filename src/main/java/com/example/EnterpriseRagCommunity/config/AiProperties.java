package com.example.EnterpriseRagCommunity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    /** DashScope OpenAI compatible base url. */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** API key. Prefer environment variable injection in production. */
    private String apiKey;

    /** Default model name. */
    private String model = "qwen3-235b-a22b-instruct-2507";

    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 300_000;

    /** Default history messages count to include (excluding current user msg). */
    private int defaultHistoryLimit = 20;
}

