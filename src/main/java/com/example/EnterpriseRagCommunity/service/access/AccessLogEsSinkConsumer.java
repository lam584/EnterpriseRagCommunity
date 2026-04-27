package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.logging.access.es-sink", name = "enabled", havingValue = "true")
public class AccessLogEsSinkConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccessLogEsSinkConsumer.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final AccessLogsRepository accessLogsRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.logging.access.es-sink.index:access-logs-v1}")
    private String esIndex = "access-logs-v1";

    @Value("${app.logging.access.es-sink.dual-verify-enabled:false}")
    private boolean dualVerifyEnabled = false;

    @Value("${app.logging.access.es-sink.dual-verify-log-on-success:false}")
    private boolean dualVerifyLogOnSuccess = false;

    @KafkaListener(
            id = "accessLogEsSinkConsumer",
            topics = "${app.logging.access.kafka-topic:access-logs-v1}",
            groupId = "${app.logging.access.es-sink.consumer-group:access-log-es-sink-v1}",
            containerFactory = "accessLogKafkaListenerContainerFactory",
            autoStartup = "${app.logging.access.es-sink.consumer-enabled:false}"
    )
    public void consume(
            String rawPayload,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String kafkaKey,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment
    ) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode data = resolveData(root);

            String requestId = text(data, "requestId");
            String traceId = text(data, "traceId");
            String eventId = firstNonBlank(text(root, "eventId"), requestId, traceId,
                    firstNonBlank(kafkaKey, text(data, "createdAt"), String.valueOf(System.currentTimeMillis())));

            Document doc = Document.create();
            doc.setId(eventId);
            doc.put("id", eventId);
            doc.put("event_id", eventId);
            doc.put("schema_version", firstNonBlank(text(root, "schemaVersion"), "access_log_event.v1"));
            doc.put("event_type", firstNonBlank(text(root, "eventType"), "ACCESS_LOG"));
            doc.put("event_source", firstNonBlank(text(root, "eventSource"), "enterprise-rag-community"));
            doc.put("event_time", firstNonBlank(text(root, "eventTime"), text(data, "createdAt")));
            doc.put("@timestamp", firstNonBlank(text(root, "eventTime"), text(data, "createdAt")));
            doc.put("kafka_topic", topic);
            doc.put("kafka_key", kafkaKey);

            doc.put("tenant_id", longVal(data, "tenantId"));
            doc.put("user_id", longVal(data, "userId"));
            doc.put("username", text(data, "username"));
            doc.put("method", text(data, "method"));
            doc.put("path", text(data, "path"));
            doc.put("query_string", text(data, "queryString"));
            doc.put("status_code", intVal(data, "statusCode"));
            doc.put("latency_ms", intVal(data, "latencyMs"));
            String clientIp = text(data, "clientIp");
            doc.put("client_ip", clientIp);
            doc.put("client_ip_text", clientIp);
            doc.put("client_port", intVal(data, "clientPort"));
            doc.put("server_ip", text(data, "serverIp"));
            doc.put("server_port", intVal(data, "serverPort"));
            doc.put("scheme", text(data, "scheme"));
            doc.put("host", text(data, "host"));
            doc.put("request_id", requestId);
            doc.put("trace_id", traceId);
            doc.put("user_agent", text(data, "userAgent"));
            doc.put("referer", text(data, "referer"));
            doc.put("details", toMap(data.get("details")));
            doc.put("created_at", text(data, "createdAt"));

            elasticsearchOperations.save(doc, IndexCoordinates.of(esIndex));
            verifyDualConsistency(eventId, requestId, traceId, kafkaKey);
            if (acknowledgment != null) acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.warn("access_log_es_sink_consume_failed topic={} key={} err={}", topic, kafkaKey, ex.getMessage());
            throw new IllegalStateException("access_log_es_sink_consume_failed", ex);
        }
    }

    private void verifyDualConsistency(String eventId, String requestId, String traceId, String kafkaKey) {
        if (!dualVerifyEnabled) return;

        boolean mysqlExists = false;
        if (requestId != null && !requestId.isBlank()) {
            mysqlExists = accessLogsRepository.existsByRequestId(requestId);
        }
        if (!mysqlExists && traceId != null && !traceId.isBlank()) {
            mysqlExists = accessLogsRepository.existsByTraceId(traceId);
        }

        if (!mysqlExists) {
            log.warn("access_log_dual_verify_miss eventId={} requestId={} traceId={} kafkaKey={}",
                    eventId, requestId, traceId, kafkaKey);
            return;
        }

        if (dualVerifyLogOnSuccess) {
            log.info("access_log_dual_verify_ok eventId={} requestId={} traceId={} kafkaKey={}",
                    eventId, requestId, traceId, kafkaKey);
        }
    }

    private JsonNode resolveData(JsonNode root) {
        JsonNode data = root.path("data");
        if (!data.isMissingNode() && !data.isNull()) return data;
        return root;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) return null;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        String out = field.asText(null);
        return out == null || out.isBlank() ? null : out.trim();
    }

    private static Integer intVal(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) return null;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        if (field.isNumber()) return field.asInt();
        try {
            return Integer.parseInt(field.asText().trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static Long longVal(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) return null;
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        if (field.isNumber()) return field.asLong();
        try {
            return Long.parseLong(field.asText().trim());
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception ex) {
            return null;
        }
    }
}
