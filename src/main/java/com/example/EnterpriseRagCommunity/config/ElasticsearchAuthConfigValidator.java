package com.example.EnterpriseRagCommunity.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Validates Elasticsearch auth configuration at startup.
 *
 * This project uses a single auth mode:
 * - ApiKey via `app.es.api-key` (recommended for ES 8+/9+)
 */
@Component
public class ElasticsearchAuthConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchAuthConfigValidator.class);

    @Value("${app.es.api-key:}")
    private String apiKey;

    @PostConstruct
    public void validate() {
        boolean hasApiKey = apiKey != null && !apiKey.isBlank();

        if (hasApiKey) {
            log.info("Elasticsearch auth mode: ApiKey (app.es.api-key is set)");
        } else {
            // Note: For local dev with security disabled, ApiKey can be empty.
            // For secured clusters, requests will fail with 401.
            log.warn("Elasticsearch auth mode: NONE (app.es.api-key is empty). If your ES has security enabled, requests will fail with 401.");
        }
    }
}
