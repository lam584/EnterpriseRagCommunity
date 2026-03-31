package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;

import java.net.HttpURLConnection;

final class ElasticsearchHttpSupport {

    private ElasticsearchHttpSupport() {
    }

    static String resolveEndpoint(SystemConfigurationService systemConfigurationService) {
        String endpoint = systemConfigurationService.getConfig("spring.elasticsearch.uris");
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "http://127.0.0.1:9200";
        }
        if (endpoint.contains(",")) {
            endpoint = endpoint.split(",")[0].trim();
        }
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint;
    }

    static void applyApiKey(HttpURLConnection connection, SystemConfigurationService systemConfigurationService) {
        String apiKey = systemConfigurationService.getConfig("APP_ES_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            connection.setRequestProperty("Authorization", "ApiKey " + apiKey.trim());
        }
    }
}
