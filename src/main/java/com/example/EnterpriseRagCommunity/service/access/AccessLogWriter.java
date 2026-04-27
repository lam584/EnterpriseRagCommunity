package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.AccessLogsEntity;
import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
import com.example.EnterpriseRagCommunity.config.AdminSetupManager;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccessLogWriter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogWriter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SCHEMA_VERSION = "access_log_event.v1";
    private static final String EVENT_TYPE = "ACCESS_LOG";
    private static final String EVENT_SOURCE = "enterprise-rag-community";

    private final AccessLogsRepository accessLogsRepository;
    private final KafkaTemplate<String, String> accessLogsKafkaTemplate;
    private final AdminSetupManager adminSetupManager;
    private final SystemConfigurationService systemConfigurationService;

    public AccessLogWriter(
            AccessLogsRepository accessLogsRepository,
            @Qualifier("accessLogsKafkaTemplate") @Nullable KafkaTemplate<String, String> accessLogsKafkaTemplate,
            @Nullable AdminSetupManager adminSetupManager,
            @Nullable SystemConfigurationService systemConfigurationService
    ) {
        this.accessLogsRepository = accessLogsRepository;
        this.accessLogsKafkaTemplate = accessLogsKafkaTemplate;
        this.adminSetupManager = adminSetupManager;
        this.systemConfigurationService = systemConfigurationService;
    }

    @Value("${app.logging.access.sink-mode:MYSQL}")
    private String sinkModeRaw = "MYSQL";

    @Value("${app.logging.access.kafka-topic:access-logs-v1}")
    private String kafkaTopic = "access-logs-v1";

    @Value("${app.logging.access.kafka-key-prefix:access}")
    private String kafkaKeyPrefix = "access";

    @Value("${app.logging.access.kafka-send-timeout-ms:3000}")
    private long kafkaSendTimeoutMs = 3000L;

    @Value("${app.logging.access.no-drop-guarantee:false}")
    private boolean noDropGuarantee = false;

    @Value("${app.logging.access.force-mysql-during-setup:true}")
    private boolean forceMysqlDuringSetup = true;

    @Value("${app.logging.access.async-enabled:false}")
    private boolean asyncEnabled = false;

    @Value("${app.logging.access.async-batch-size:200}")
    private int asyncBatchSize = 200;

    @Value("${app.logging.access.async-queue-capacity:50000}")
    private int asyncQueueCapacity = 50_000;

    @Value("${app.logging.access.async-drop-when-full:true}")
    private boolean asyncDropWhenFull = true;

    private final ConcurrentLinkedQueue<AccessLogsEntity> asyncQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger asyncQueueSize = new AtomicInteger(0);

    public void write(AccessLogsEntity e) {
        if (e == null) return;
        normalizeEntity(e);

        // During setup, default to MySQL unless explicitly disabled for local/dev verification.
        if (forceMysqlDuringSetup && adminSetupManager != null && adminSetupManager.isSetupRequired()) {
            persistMysql(e, noDropGuarantee);
            return;
        }

        AccessLogSinkMode mode = resolveSinkMode();
        switch (mode) {
            case MYSQL -> persistMysql(e, noDropGuarantee);
            case KAFKA -> sendToKafka(e, false);
            case DUAL -> {
                boolean sent = sendToKafka(e, false);
                if (sent) {
                    persistMysql(e, noDropGuarantee);
                } else {
                    persistMysqlSync(e);
                }
            }
        }
    }

    public void write(
            Long tenantId,
            Long userId,
            String username,
            String method,
            String path,
            String queryString,
            Integer statusCode,
            Integer latencyMs,
            String clientIp,
            Integer clientPort,
            String serverIp,
            Integer serverPort,
            String scheme,
            String host,
            String requestId,
            String traceId,
            String userAgent,
            String referer,
            Map<String, Object> details
    ) {
        AccessLogsEntity e = new AccessLogsEntity();
        e.setTenantId(tenantId);
        e.setUserId(userId);
        e.setUsername(username);
        e.setMethod(method == null ? "UNKNOWN" : method);
        e.setPath(path == null ? "/" : path);
        e.setQueryString(queryString);
        e.setStatusCode(statusCode);
        e.setLatencyMs(latencyMs);
        e.setClientIp(clientIp);
        e.setClientPort(clientPort);
        e.setServerIp(serverIp);
        e.setServerPort(serverPort);
        e.setScheme(scheme);
        e.setHost(host);
        e.setRequestId(requestId);
        e.setTraceId(traceId);
        e.setUserAgent(userAgent);
        e.setReferer(referer);
        e.setDetails(details);
        e.setCreatedAt(LocalDateTime.now());
        write(e);
    }

    @Scheduled(fixedDelayString = "${app.logging.access.async-flush-interval-ms:200}")
    public void flushAsyncQueue() {
        if (!asyncEnabled) return;
        flushBatch(Math.max(1, asyncBatchSize));
    }

    @PreDestroy
    public void flushBeforeShutdown() {
        if (!asyncEnabled) return;
        int batchSize = Math.max(1, asyncBatchSize);
        int flushed;
        do {
            flushed = flushBatch(batchSize);
        } while (flushed > 0);
    }

    private void enqueueAsync(AccessLogsEntity e, boolean forceNoDrop) {
        int capacity = Math.max(1, asyncQueueCapacity);
        int current = asyncQueueSize.incrementAndGet();
        if (current > capacity) {
            asyncQueueSize.decrementAndGet();
            if (asyncDropWhenFull && !forceNoDrop) {
                return;
            }
            accessLogsRepository.save(e);
            return;
        }
        asyncQueue.offer(e);
    }

    private int flushBatch(int batchSize) {
        List<AccessLogsEntity> batch = new ArrayList<>(batchSize);
        while (batch.size() < batchSize) {
            AccessLogsEntity next = asyncQueue.poll();
            if (next == null) break;
            asyncQueueSize.decrementAndGet();
            batch.add(next);
        }
        if (batch.isEmpty()) return 0;
        accessLogsRepository.saveAll(batch);
        return batch.size();
    }

    private static void normalizeEntity(AccessLogsEntity e) {
        if (e.getCreatedAt() == null) e.setCreatedAt(LocalDateTime.now());
        if (e.getMethod() == null || e.getMethod().isBlank()) e.setMethod("UNKNOWN");
        if (e.getPath() == null || e.getPath().isBlank()) e.setPath("/");
    }

    private AccessLogSinkMode resolveSinkMode() {
        String raw = resolveConfig("app.logging.access.sink-mode", sinkModeRaw);
        if (raw == null || raw.isBlank()) return AccessLogSinkMode.MYSQL;
        try {
            return AccessLogSinkMode.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            log.warn("Unknown app.logging.access.sink-mode='{}', fallback MYSQL", raw);
            return AccessLogSinkMode.MYSQL;
        }
    }

    private void persistMysql(AccessLogsEntity e, boolean forceNoDrop) {
        if (!asyncEnabled) {
            accessLogsRepository.save(e);
            return;
        }
        enqueueAsync(e, forceNoDrop);
    }

    private void persistMysqlSync(AccessLogsEntity e) {
        try {
            accessLogsRepository.save(e);
        } catch (Exception ex) {
            log.error("access_log_fallback_mysql_persist_failed requestId={} traceId={} err={}",
                    e.getRequestId(), e.getTraceId(), ex.getMessage());
        }
    }

    private boolean sendToKafka(AccessLogsEntity e, boolean fallbackMysqlOnAsyncFailure) {
        String resolvedKafkaTopic = resolveKafkaTopic();
        String resolvedKafkaKeyPrefix = resolveKafkaKeyPrefix();
        long resolvedKafkaSendTimeoutMs = resolveKafkaSendTimeoutMs();

        if (accessLogsKafkaTemplate == null) {
            log.warn("access_log_kafka_template_missing topic={} mode={} fallback=mysql", resolvedKafkaTopic, resolveSinkMode());
            return false;
        }
        try {
            String key = kafkaMessageKey(e, resolvedKafkaKeyPrefix);
            String payload = MAPPER.writeValueAsString(toKafkaPayload(e, key));
            CompletableFuture<?> sendFuture = accessLogsKafkaTemplate.send(resolvedKafkaTopic, key, payload);

            // Fail-fast for immediately failed futures to avoid duplicate fallback writes.
            if (sendFuture.isCompletedExceptionally()) {
                try {
                    sendFuture.join();
                } catch (Exception ex) {
                    log.warn("access_log_kafka_send_failed topic={} requestId={} traceId={} err={}",
                            resolvedKafkaTopic, e.getRequestId(), e.getTraceId(), ex.getMessage());
                }
                return false;
            }

            sendFuture
                    .orTimeout(Math.max(100L, resolvedKafkaSendTimeoutMs), TimeUnit.MILLISECONDS)
                    .whenComplete((ignored, throwable) -> {
                        if (throwable == null) return;
                        log.warn("access_log_kafka_send_failed topic={} requestId={} traceId={} err={}",
                                resolvedKafkaTopic, e.getRequestId(), e.getTraceId(), throwable.getMessage());
                        if (fallbackMysqlOnAsyncFailure) {
                            persistMysqlSync(e);
                        }
                    });
            return true;
        } catch (Exception ex) {
            log.warn("access_log_kafka_send_failed topic={} requestId={} traceId={} err={}",
                    resolvedKafkaTopic, e.getRequestId(), e.getTraceId(), ex.getMessage());
            return false;
        }
    }

    private String resolveKafkaTopic() {
        return resolveConfig("app.logging.access.kafka-topic", kafkaTopic);
    }

    private String resolveKafkaKeyPrefix() {
        return resolveConfig("app.logging.access.kafka-key-prefix", kafkaKeyPrefix);
    }

    private long resolveKafkaSendTimeoutMs() {
        String raw = resolveConfig("app.logging.access.kafka-send-timeout-ms", Long.toString(kafkaSendTimeoutMs));
        if (raw == null || raw.isBlank()) return kafkaSendTimeoutMs;
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return kafkaSendTimeoutMs;
        }
    }

    private String resolveConfig(String key, String fallback) {
        if (systemConfigurationService == null) return fallback;
        String value = systemConfigurationService.getConfig(key);
        if (value == null || value.isBlank()) return fallback;
        return value;
    }

    private static String kafkaMessageKey(AccessLogsEntity e, String keyPrefixRaw) {
        String keyPrefix = sanitizeKeyPart(keyPrefixRaw, 24);
        String tenantPart = e.getTenantId() == null ? "t0" : "t" + e.getTenantId();
        String routeSeed = firstNonBlank(e.getRequestId(), e.getTraceId(), e.getPath(),
                e.getCreatedAt() == null ? null : e.getCreatedAt().toString(), "na");
        String routePart = sanitizeKeyPart(routeSeed, 96);
        return keyPrefix + "|" + tenantPart + "|" + routePart;
    }

    private static Map<String, Object> toKafkaPayload(AccessLogsEntity e, String kafkaKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("eventType", EVENT_TYPE);
        payload.put("eventSource", EVENT_SOURCE);
        payload.put("eventId", buildEventId(e, kafkaKey));
        payload.put("eventTime", e.getCreatedAt() == null ? LocalDateTime.now().toString() : e.getCreatedAt().toString());
        payload.put("key", kafkaKey);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", e.getId());
        data.put("tenantId", e.getTenantId());
        data.put("userId", e.getUserId());
        data.put("username", e.getUsername());
        data.put("method", e.getMethod());
        data.put("path", e.getPath());
        data.put("queryString", e.getQueryString());
        data.put("statusCode", e.getStatusCode());
        data.put("latencyMs", e.getLatencyMs());
        data.put("clientIp", e.getClientIp());
        data.put("clientPort", e.getClientPort());
        data.put("serverIp", e.getServerIp());
        data.put("serverPort", e.getServerPort());
        data.put("scheme", e.getScheme());
        data.put("host", e.getHost());
        data.put("requestId", e.getRequestId());
        data.put("traceId", e.getTraceId());
        data.put("userAgent", e.getUserAgent());
        data.put("referer", e.getReferer());
        data.put("details", e.getDetails());
        data.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        payload.put("data", data);
        return payload;
    }

    private static String buildEventId(AccessLogsEntity e, String kafkaKey) {
        String fromReq = firstNonBlank(e.getRequestId(), e.getTraceId());
        if (fromReq != null) return fromReq;
        return kafkaKey + "|" + (e.getCreatedAt() == null ? LocalDateTime.now() : e.getCreatedAt());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static String sanitizeKeyPart(String raw, int maxLen) {
        String v = raw == null || raw.isBlank() ? "na" : raw.trim();
        String cleaned = v.replaceAll("[^a-zA-Z0-9._:/-]", "_");
        if (cleaned.isBlank()) cleaned = "na";
        if (cleaned.length() <= maxLen) return cleaned;
        return cleaned.substring(0, Math.max(2, maxLen));
    }
}

