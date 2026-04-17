package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.ElasticsearchHttpSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.logging.access.es-sink", name = "enabled", havingValue = "true")
public class AccessLogEsIndexTemplateInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccessLogEsIndexTemplateInitializer.class);

    private final SystemConfigurationService systemConfigurationService;
    private final ObjectMapper objectMapper;

    @Value("${app.logging.access.es-sink.auto-init-template:true}")
    private boolean autoInitTemplate = true;

    @Value("${app.logging.access.es-sink.template-name:access-logs-template-v1}")
    private String templateName = "access-logs-template-v1";

    @Value("${app.logging.access.es-sink.index-pattern:access-logs-v1*}")
    private String indexPattern = "access-logs-v1*";

    @Value("${app.logging.access.es-sink.template-shards:1}")
    private int templateShards = 1;

    @Value("${app.logging.access.es-sink.template-replicas:1}")
    private int templateReplicas = 1;

    @Override
    public void run(ApplicationArguments args) {
        if (!autoInitTemplate) return;
        upsertIndexTemplate();
    }

    private void upsertIndexTemplate() {
        String endpoint = ElasticsearchHttpSupport.resolveEndpoint(systemConfigurationService);
        String safeTemplateName = templateName == null || templateName.isBlank()
                ? "access-logs-template-v1"
                : templateName.trim();

        try {
            String encodedName = URLEncoder.encode(safeTemplateName, StandardCharsets.UTF_8);
            URL url = java.net.URI.create(endpoint + "/_index_template/" + encodedName).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            ElasticsearchHttpSupport.applyApiKey(conn, systemConfigurationService);

            String payload = objectMapper.writeValueAsString(buildTemplateBody());
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String body = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                log.warn("access_log_es_template_upsert_failed template={} code={} body={}", safeTemplateName, code, body);
                return;
            }
            log.info("access_log_es_template_upsert_ok template={} pattern={}", safeTemplateName, indexPattern);
        } catch (Exception ex) {
            log.warn("access_log_es_template_upsert_error template={} err={}", safeTemplateName, ex.getMessage());
        }
    }

    private Map<String, Object> buildTemplateBody() {
        String pattern = indexPattern == null || indexPattern.isBlank() ? "access-logs-v1*" : indexPattern.trim();

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("number_of_shards", Math.max(1, templateShards));
        settings.put("number_of_replicas", Math.max(0, templateReplicas));

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
        root.put("index_patterns", List.of(pattern));
        root.put("priority", 200);
        root.put("template", template);
        return root;
    }
}
