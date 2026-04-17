package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.AccessLogEsIndexStatusDTO;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.ElasticsearchHttpSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AccessLogEsIndexStatusService {

    private static final String DEFAULT_ACCESS_INDEX = "access-logs-v1";

    private final SystemConfigurationService systemConfigurationService;
    private final ObjectMapper objectMapper;

    public AccessLogEsIndexStatusDTO getStatus() {
        String indexName = conf("app.logging.access.es-sink.index", DEFAULT_ACCESS_INDEX);
        String sinkMode = conf("app.logging.access.sink-mode", "MYSQL").trim().toUpperCase();
        boolean esSinkEnabled = boolConf("app.logging.access.es-sink.enabled", false);
        boolean consumerEnabled = boolConf("app.logging.access.es-sink.consumer-enabled", false);

        boolean exists = false;
        boolean available = false;
        String health = null;
        String status = null;
        Long docsCount = null;
        String storeSize = null;
        String availabilityMessage = null;

        try {
            String endpoint = ElasticsearchHttpSupport.resolveEndpoint(systemConfigurationService);
            String encodedIndex = URLEncoder.encode(indexName, StandardCharsets.UTF_8);
            String catApi = endpoint + "/_cat/indices/" + encodedIndex + "?format=json&h=index,health,status,docs.count,store.size";
            URL url = java.net.URI.create(catApi).toURL();

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(8000);
            ElasticsearchHttpSupport.applyApiKey(conn, systemConfigurationService);

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String body = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);

            if (code == 404) {
                availabilityMessage = "索引不存在";
            } else if (code >= 200 && code < 300) {
                JsonNode root = objectMapper.readTree(body);
                JsonNode first = root.isArray() && !root.isEmpty() ? root.get(0) : null;
                if (first == null || first.isNull()) {
                    availabilityMessage = "索引不存在";
                } else {
                    exists = true;
                    health = text(first, "health");
                    status = text(first, "status");
                    storeSize = text(first, "store.size");
                    docsCount = parseLong(text(first, "docs.count"));
                    available = !"close".equalsIgnoreCase(status);
                    if (!available) {
                        availabilityMessage = "索引状态为 close";
                    }
                }
            } else {
                availabilityMessage = "查询失败（HTTP " + code + "）";
            }
        } catch (Exception e) {
            availabilityMessage = "索引状态查询失败：" + e.getMessage();
        }

        if (availabilityMessage == null && !esSinkEnabled) {
            availabilityMessage = "ES sink 未启用";
        }

        return new AccessLogEsIndexStatusDTO(
                indexName,
                indexName,
                sinkMode,
                esSinkEnabled,
                consumerEnabled,
                exists,
                available,
                health,
                status,
                docsCount,
                storeSize,
                availabilityMessage
        );
    }

    private String conf(String key, String defaultValue) {
        String v = systemConfigurationService.getConfig(key);
        if (v == null || v.isBlank()) return defaultValue;
        return v.trim();
    }

    private boolean boolConf(String key, boolean defaultValue) {
        String v = systemConfigurationService.getConfig(key);
        if (v == null || v.isBlank()) return defaultValue;
        return "true".equalsIgnoreCase(v.trim());
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) return null;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        String out = field.asText(null);
        if (out == null) return null;
        String trimmed = out.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Long parseLong(String text) {
        if (text == null || text.isBlank()) return null;
        String normalized = text.replace(",", "").trim();
        try {
            return Long.parseLong(normalized);
        } catch (Exception e) {
            return null;
        }
    }
}
