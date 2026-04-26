package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.ElasticsearchHttpSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccessLogEsIndexProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(AccessLogEsIndexProvisioningService.class);
    private static final String DEFAULT_ACCESS_INDEX = "access-logs-v1";
    private static final String DEFAULT_TEMPLATE_NAME = "access-logs-template-v1";
    private static final String DEFAULT_INDEX_PATTERN = "access-logs-v1*";

    private final SystemConfigurationService systemConfigurationService;
    private final ObjectMapper objectMapper;

    public void initializeFromCurrentConfig() {
        initialize(
                ElasticsearchHttpSupport.resolveEndpoint(systemConfigurationService),
                systemConfigurationService.getConfig("APP_ES_API_KEY"),
                Map.of()
        );
    }

    public void initialize(String endpoint, String apiKey, Map<String, String> configs) {
        String safeEndpoint = endpoint == null ? "" : endpoint.trim();
        if (safeEndpoint.isEmpty()) {
            throw new IllegalArgumentException("Elasticsearch endpoint is required");
        }

        Map<String, String> safeConfigs = configs == null ? Map.of() : configs;
        String indexName = resolveConfig(safeConfigs, "app.logging.access.es-sink.index", DEFAULT_ACCESS_INDEX);
        String templateName = resolveConfig(safeConfigs, "app.logging.access.es-sink.template-name", DEFAULT_TEMPLATE_NAME);
        String indexPattern = resolveConfig(safeConfigs, "app.logging.access.es-sink.index-pattern", DEFAULT_INDEX_PATTERN);
        int shards = resolveIntConfig(safeConfigs, "app.logging.access.es-sink.template-shards", 1, 1);
        int replicas = resolveIntConfig(safeConfigs, "app.logging.access.es-sink.template-replicas", 1, 0);

        upsertIndexTemplate(safeEndpoint, apiKey, templateName, indexPattern, shards, replicas);
        ensureIndexExists(safeEndpoint, apiKey, indexName);
        ensureExistingIndexClientIpTextMapping(safeEndpoint, apiKey, indexName);
    }

    private void upsertIndexTemplate(String endpoint, String apiKey, String templateName, String indexPattern, int shards, int replicas) {
        try {
            String encodedName = URLEncoder.encode(templateName, StandardCharsets.UTF_8);
            URL url = java.net.URI.create(endpoint + "/_index_template/" + encodedName).toURL();
            HttpURLConnection conn = openJsonConnection(url, "PUT", apiKey, true);

            String payload = objectMapper.writeValueAsString(buildTemplateBody(indexPattern, shards, replicas));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            ResponseInfo response = readResponse(conn);
            if (!response.isSuccess()) {
                throw new IllegalStateException("Template upsert failed: HTTP " + response.code + " " + response.body);
            }
            log.info("access_log_es_template_upsert_ok template={} pattern={}", templateName, indexPattern);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upsert access log index template: " + ex.getMessage(), ex);
        }
    }

    private void ensureIndexExists(String endpoint, String apiKey, String indexName) {
        try {
            String encodedIndex = URLEncoder.encode(indexName, StandardCharsets.UTF_8);
            URL headUrl = java.net.URI.create(endpoint + "/" + encodedIndex).toURL();
            HttpURLConnection head = openJsonConnection(headUrl, "HEAD", apiKey, false);
            int headCode = head.getResponseCode();
            if (headCode >= 200 && headCode < 300) {
                return;
            }
            if (headCode != 404) {
                String body = readResponse(head).body;
                throw new IllegalStateException("Index check failed: HTTP " + headCode + " " + body);
            }

            URL putUrl = java.net.URI.create(endpoint + "/" + encodedIndex).toURL();
            HttpURLConnection put = openJsonConnection(putUrl, "PUT", apiKey, true);
            try (OutputStream os = put.getOutputStream()) {
                os.write("{}".getBytes(StandardCharsets.UTF_8));
            }
            ResponseInfo created = readResponse(put);
            if (!created.isSuccess() && !created.body.contains("resource_already_exists_exception")) {
                throw new IllegalStateException("Index create failed: HTTP " + created.code + " " + created.body);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to ensure access log index exists: " + ex.getMessage(), ex);
        }
    }

    private void ensureExistingIndexClientIpTextMapping(String endpoint, String apiKey, String indexName) {
        try {
            String encodedIndex = URLEncoder.encode(indexName, StandardCharsets.UTF_8);
            URL url = java.net.URI.create(endpoint + "/" + encodedIndex + "/_mapping").toURL();
            HttpURLConnection conn = openJsonConnection(url, "PUT", apiKey, true);

            String payload = objectMapper.writeValueAsString(buildCurrentIndexMappingBody());
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            ResponseInfo response = readResponse(conn);
            if (response.code == 404) {
                throw new IllegalStateException("Index is still missing after create");
            }
            if (!response.isSuccess()) {
                throw new IllegalStateException("Mapping upsert failed: HTTP " + response.code + " " + response.body);
            }
            log.info("access_log_es_index_mapping_upsert_ok index={} field=client_ip_text", indexName);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to update access log index mapping: " + ex.getMessage(), ex);
        }
    }

    private HttpURLConnection openJsonConnection(URL url, String method, String apiKey, boolean doOutput) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(doOutput);
        conn.setRequestProperty("Content-Type", "application/json");
        applyApiKey(conn, apiKey);
        return conn;
    }

    private void applyApiKey(HttpURLConnection conn, String apiKey) {
        if (conn == null) return;
        String safeApiKey = apiKey == null ? null : apiKey.trim();
        if (safeApiKey == null || safeApiKey.isEmpty()) return;
        conn.setRequestProperty("Authorization", "ApiKey " + safeApiKey);
    }

    private ResponseInfo readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
        return new ResponseInfo(code, body);
    }

    private String resolveConfig(Map<String, String> configs, String key, String defaultValue) {
        String value = configs.get(key);
        if (value != null && !value.isBlank()) return value.trim();
        String stored = systemConfigurationService.getConfig(key);
        if (stored != null && !stored.isBlank()) return stored.trim();
        return defaultValue;
    }

    private int resolveIntConfig(Map<String, String> configs, String key, int defaultValue, int minValue) {
        String value = resolveConfig(configs, key, String.valueOf(defaultValue));
        try {
            return Math.max(minValue, Integer.parseInt(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Map<String, Object> buildTemplateBody(String indexPattern, int shards, int replicas) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("number_of_shards", shards);
        settings.put("number_of_replicas", replicas);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("event_id", Map.of("type", "keyword"));
        props.put("schema_version", Map.of("type", "keyword"));
        props.put("event_type", Map.of("type", "keyword"));
        props.put("event_source", Map.of("type", "keyword"));
        props.put("event_time", Map.of("type", "date"));
        props.put("@timestamp", Map.of("type", "date"));
        props.put("kafka_topic", Map.of("type", "keyword"));
        props.put("kafka_key", Map.of("type", "keyword"));
        props.put("tenant_id", Map.of("type", "long"));
        props.put("user_id", Map.of("type", "long"));
        props.put("username", Map.of("type", "keyword"));
        props.put("method", Map.of("type", "keyword"));
        props.put("path", Map.of("type", "keyword"));
        props.put("query_string", Map.of("type", "keyword"));
        props.put("status_code", Map.of("type", "integer"));
        props.put("latency_ms", Map.of("type", "integer"));
        props.put("client_ip", Map.of("type", "ip", "ignore_malformed", true));
        props.put("client_ip_text", Map.of("type", "keyword"));
        props.put("client_port", Map.of("type", "integer"));
        props.put("server_ip", Map.of("type", "ip", "ignore_malformed", true));
        props.put("server_port", Map.of("type", "integer"));
        props.put("scheme", Map.of("type", "keyword"));
        props.put("host", Map.of("type", "keyword"));
        props.put("request_id", Map.of("type", "keyword"));
        props.put("trace_id", Map.of("type", "keyword"));
        props.put("user_agent", Map.of("type", "text"));
        props.put("referer", Map.of("type", "keyword"));
        props.put("created_at", Map.of("type", "date"));
        props.put("details", Map.of("type", "object", "dynamic", true));

        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("dynamic", true);
        mappings.put("properties", props);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("settings", settings);
        template.put("mappings", mappings);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("index_patterns", List.of(indexPattern));
        root.put("priority", 200);
        root.put("template", template);
        return root;
    }

    private Map<String, Object> buildCurrentIndexMappingBody() {
        return Map.of(
                "properties",
                Map.of("client_ip_text", Map.of("type", "keyword"))
        );
    }

    private record ResponseInfo(int code, String body) {
        private boolean isSuccess() {
            return code >= 200 && code < 300;
        }
    }
}
