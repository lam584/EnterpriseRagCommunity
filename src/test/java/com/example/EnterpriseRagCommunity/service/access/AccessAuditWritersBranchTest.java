package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.AccessLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessAuditWritersBranchTest {

    @AfterEach
    void cleanup() {
        RequestAuditContextHolder.clear();
        AuditLogContextHolder.clear();
    }

    @Test
    void accessLogWriter_should_fill_defaults_and_save() {
        AccessLogsRepository repo = mock(AccessLogsRepository.class);
        AccessLogWriter writer = new AccessLogWriter(repo, null);

        writer.write((AccessLogsEntity) null);

        AccessLogsEntity e = new AccessLogsEntity();
        writer.write(e);
        assertNotNull(e.getCreatedAt());
        assertEquals("UNKNOWN", e.getMethod());
        assertEquals("/", e.getPath());
        verify(repo).save(e);
    }

    @Test
    void accessLogWriter_overload_should_handle_null_method_and_path() {
        AccessLogsRepository repo = mock(AccessLogsRepository.class);
        AccessLogWriter writer = new AccessLogWriter(repo, null);
        ArgumentCaptor<AccessLogsEntity> cap = ArgumentCaptor.forClass(AccessLogsEntity.class);

        writer.write(
                1L, 2L, "u",
                null, null, "a=1", 200, 12,
                "127.0.0.1", 11, "10.0.0.1", 22,
                "https", "h", "r", "t", "ua", "ref",
                Map.of("k", "v")
        );

        verify(repo).save(cap.capture());
        AccessLogsEntity out = cap.getValue();
        assertEquals("UNKNOWN", out.getMethod());
        assertEquals("/", out.getPath());
        assertNotNull(out.getCreatedAt());
        assertEquals("v", out.getDetails().get("k"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void accessLogWriter_kafka_mode_should_not_write_mysql_when_kafka_fails() {
        AccessLogsRepository repo = mock(AccessLogsRepository.class);
        AccessLogWriter writer = new AccessLogWriter(repo, null);

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        ReflectionTestUtils.setField(writer, "accessLogsKafkaTemplate", kafkaTemplate);
        ReflectionTestUtils.setField(writer, "sinkModeRaw", "KAFKA");
        ReflectionTestUtils.setField(writer, "kafkaTopic", "access-logs-v1");
        ReflectionTestUtils.setField(writer, "asyncEnabled", false);

        AccessLogsEntity e = new AccessLogsEntity();
        e.setRequestId("r1");
        e.setTraceId("t1");
        e.setMethod("GET");
        e.setPath("/p");
        writer.write(e);

        verify(repo, never()).save(e);
    }

    @Test
    @SuppressWarnings("unchecked")
    void accessLogWriter_kafka_mode_should_not_write_mysql_when_kafka_succeeds() {
        AccessLogsRepository repo = mock(AccessLogsRepository.class);
        AccessLogWriter writer = new AccessLogWriter(repo, null);

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> ok = new CompletableFuture<>();
        ok.complete(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(ok);

        ReflectionTestUtils.setField(writer, "accessLogsKafkaTemplate", kafkaTemplate);
        ReflectionTestUtils.setField(writer, "sinkModeRaw", "KAFKA");
        ReflectionTestUtils.setField(writer, "kafkaTopic", "access-logs-v1");
        ReflectionTestUtils.setField(writer, "kafkaKeyPrefix", "access");
        ReflectionTestUtils.setField(writer, "asyncEnabled", false);

        AccessLogsEntity e = new AccessLogsEntity();
        e.setRequestId("r2");
        e.setTraceId("t2");
        e.setMethod("GET");
        e.setPath("/ok");
        writer.write(e);

        verify(repo, never()).save(e);

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCap = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), keyCap.capture(), payloadCap.capture());

        assertTrue(keyCap.getValue().startsWith("access|"));
        try {
            JsonNode root = new ObjectMapper().readTree(payloadCap.getValue());
            assertEquals("access_log_event.v1", root.path("schemaVersion").asText());
            assertEquals("ACCESS_LOG", root.path("eventType").asText());
            assertEquals("r2", root.path("eventId").asText());
            assertEquals("r2", root.path("data").path("requestId").asText());
            assertEquals("/ok", root.path("data").path("path").asText());
        } catch (Exception ex) {
            fail("payload should be valid json: " + ex.getMessage());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void accessLogWriter_dual_mode_should_write_mysql_when_kafka_succeeds() {
        AccessLogsRepository repo = mock(AccessLogsRepository.class);
        AccessLogWriter writer = new AccessLogWriter(repo, null);

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> ok = new CompletableFuture<>();
        ok.complete(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(ok);

        ReflectionTestUtils.setField(writer, "accessLogsKafkaTemplate", kafkaTemplate);
        ReflectionTestUtils.setField(writer, "sinkModeRaw", "DUAL");
        ReflectionTestUtils.setField(writer, "kafkaTopic", "access-logs-v1");
        ReflectionTestUtils.setField(writer, "kafkaKeyPrefix", "access");
        ReflectionTestUtils.setField(writer, "asyncEnabled", false);

        AccessLogsEntity e = new AccessLogsEntity();
        e.setRequestId("r-dual");
        e.setTraceId("t-dual");
        e.setMethod("GET");
        e.setPath("/dual");
        writer.write(e);

        verify(repo).save(e);
    }

    @Test
    void auditLogWriter_should_merge_context_and_sanitize_sensitive_fields() {
        AuditLogsRepository repo = mock(AuditLogsRepository.class);
        AuditLogWriter writer = new AuditLogWriter(repo);
        ArgumentCaptor<AuditLogsEntity> cap = ArgumentCaptor.forClass(AuditLogsEntity.class);

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("token", "raw-token");
        nested.put("message", "authorization=abc");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("password", "123");
        details.put("safe", "ok");
        details.put("obj", nested);
        details.put("list", List.of(Map.of("privateKey", "k"), "x"));

        RequestAuditContextHolder.set(new RequestAuditContextHolder.RequestAuditContext(
                "ctx-req", "ctx-trace", "1.1.1.1", 1, "2.2.2.2", 2,
                "POST", "/p", "https", "h", "ua", "ref", Map.of("body", "v")
        ));

        writer.write(
                9L, "actor", null, null, 8L,
                null, "password=abc token=def", null, details
        );

        verify(repo).save(cap.capture());
        AuditLogsEntity out = cap.getValue();
        assertEquals("UNKNOWN", out.getAction());
        assertEquals("UNKNOWN", out.getEntityType());
        assertEquals(AuditResult.FAIL, out.getResult());
        assertEquals(9L, out.getActorUserId());
        assertTrue(AuditLogContextHolder.wasWritten());

        Map<String, Object> d = out.getDetails();
        assertEquals("***", d.get("password"));
        assertEquals("ok", d.get("safe"));
        assertEquals("actor", d.get("actorName"));
        assertEquals("ctx-trace", d.get("traceId"));
        assertEquals("1.1.1.1", d.get("ip"));
        assertEquals("ctx-req", d.get("requestId"));
        assertEquals("POST", d.get("method"));
        assertEquals("/p", d.get("path"));
        assertEquals("https", d.get("scheme"));
        assertEquals("h", d.get("host"));
        assertEquals(1, d.get("clientPort"));
        assertEquals("2.2.2.2", d.get("serverIp"));
        assertEquals(2, d.get("serverPort"));
        assertEquals("ua", d.get("userAgent"));
        assertEquals("ref", d.get("referer"));
        assertEquals(Map.of("body", "v"), d.get("req"));
        assertTrue(String.valueOf(d.get("message")).contains("***"));

        Object obj = d.get("obj");
        assertTrue(obj instanceof Map);
        Map<?, ?> nestedOut = (Map<?, ?>) obj;
        assertEquals("***", nestedOut.get("token"));
        assertTrue(String.valueOf(nestedOut.get("message")).contains("***"));
    }

    @Test
    void auditLogWriter_writeSystem_should_use_explicit_values_and_keep_trace_when_provided() {
        AuditLogsRepository repo = mock(AuditLogsRepository.class);
        when(repo.save(org.mockito.ArgumentMatchers.any(AuditLogsEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        AuditLogWriter writer = new AuditLogWriter(repo);
        ArgumentCaptor<AuditLogsEntity> cap = ArgumentCaptor.forClass(AuditLogsEntity.class);

        RequestAuditContextHolder.set(new RequestAuditContextHolder.RequestAuditContext(
                "ctx-req", "ctx-trace", null, null, null, null,
                null, null, null, null, null, null, null
        ));

        writer.writeSystem(
                "A", "E", 1L, AuditResult.SUCCESS, "{\"token\":\"a\"}", "explicit-trace",
                Map.of("cookie", "raw")
        );

        verify(repo).save(cap.capture());
        AuditLogsEntity out = cap.getValue();
        assertEquals("A", out.getAction());
        assertEquals("E", out.getEntityType());
        assertEquals(AuditResult.SUCCESS, out.getResult());
        assertEquals(1L, out.getEntityId());
        assertNotNull(out.getCreatedAt());
        assertEquals("***", out.getDetails().get("cookie"));
        assertEquals("explicit-trace", out.getDetails().get("traceId"));
    }

    @Test
    void requestAuditContextHolder_should_set_get_and_clear() {
        assertNull(RequestAuditContextHolder.get());
        RequestAuditContextHolder.RequestAuditContext ctx = new RequestAuditContextHolder.RequestAuditContext(
                "r", "t", "ip", 1, "sip", 2, "GET", "/", "http", "h", "ua", "ref", Map.of("k", "v")
        );
        RequestAuditContextHolder.set(ctx);
        assertSame(ctx, RequestAuditContextHolder.get());
        RequestAuditContextHolder.set(null);
        assertNull(RequestAuditContextHolder.get());
        RequestAuditContextHolder.set(ctx);
        RequestAuditContextHolder.clear();
        assertNull(RequestAuditContextHolder.get());
    }
}
