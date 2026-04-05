package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ElasticsearchHttpSupport {

    private ElasticsearchHttpSupport() {
    }

    public static String resolveEndpoint(SystemConfigurationService systemConfigurationService) {
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

    public static void applyApiKey(HttpURLConnection connection, SystemConfigurationService systemConfigurationService) {
        String apiKey = systemConfigurationService.getConfig("APP_ES_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            connection.setRequestProperty("Authorization", "ApiKey " + apiKey.trim());
        }
    }

    public static void deleteByQuery(SystemConfigurationService systemConfigurationService, String indexName, String body) {
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName is blank");
        }
        String endpoint = resolveEndpoint(systemConfigurationService);
        try {
            URL url = java.net.URI.create(endpoint + "/" + indexName.trim() + "/_delete_by_query?conflicts=proceed&refresh=true").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(30_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            applyApiKey(conn, systemConfigurationService);
            String payload = body == null ? "{\"query\":{\"match_all\":{}}}" : body;
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String json = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("ES delete_by_query HTTP " + code + ": " + json);
            }
        } catch (Exception e) {
            throw new IllegalStateException("ES delete_by_query failed: " + e.getMessage(), e);
        }
    }

    public static void deleteIndex(SystemConfigurationService systemConfigurationService, String indexName) {
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName is blank");
        }
        String endpoint = resolveEndpoint(systemConfigurationService);
        try {
            String idx = URLEncoder.encode(indexName.trim(), StandardCharsets.UTF_8);
            URL url = java.net.URI.create(endpoint + "/" + idx + "?ignore_unavailable=true").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("Content-Type", "application/json");
            applyApiKey(conn, systemConfigurationService);
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String json = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if ((code >= 200 && code < 300) || code == 404) {
                return;
            }
            throw new IllegalStateException("ES delete index HTTP " + code + ": " + json);
        } catch (Exception e) {
            throw new IllegalStateException("ES delete index failed: " + e.getMessage(), e);
        }
    }
}
