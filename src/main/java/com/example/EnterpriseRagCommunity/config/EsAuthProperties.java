package com.example.EnterpriseRagCommunity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch authentication configuration.
 *
 * For ES 8+/9+ security-enabled clusters, ApiKey is recommended.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.es")
public class EsAuthProperties {

    /**
     * Elasticsearch ApiKey (the raw key string used after "Authorization: ApiKey ").
     *
     * Suggest injecting via environment variable APP_ES_API_KEY.
     */
    private String apiKey;
}
