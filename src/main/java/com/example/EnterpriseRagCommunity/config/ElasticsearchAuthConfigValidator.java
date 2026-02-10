package com.example.EnterpriseRagCommunity.config;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validates Elasticsearch auth configuration at startup.
 *
 * This project uses a single auth mode:
 * - ApiKey via `app.es.api-key` (recommended for ES 8+/9+)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchAuthConfigValidator {

    private final SystemConfigurationService systemConfigurationService;

    @PostConstruct
    public void validate() {
        String apiKey = systemConfigurationService.getConfig("APP_ES_API_KEY");
        if (!StringUtils.hasText(apiKey)) {
            log.warn("Elasticsearch auth mode: NONE (APP_ES_API_KEY in system_configurations is empty). If your ES has security enabled, requests will fail with 401.");
        } else {
            log.info("Elasticsearch auth mode: API Key configured in database.");
        }
    }
}
