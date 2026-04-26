package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessLogEsSinkConsumerBranchTest {

    @Test
    void consume_should_save_to_es_and_verify_mysql_when_dual_verify_enabled() {
        ElasticsearchOperations esOps = mock(ElasticsearchOperations.class);
        AccessLogsRepository accessLogsRepository = mock(AccessLogsRepository.class);
        AccessLogEsSinkConsumer consumer = new AccessLogEsSinkConsumer(esOps, accessLogsRepository, new ObjectMapper());

        ReflectionTestUtils.setField(consumer, "esIndex", "access-logs-v1");
        ReflectionTestUtils.setField(consumer, "dualVerifyEnabled", true);
        ReflectionTestUtils.setField(consumer, "dualVerifyLogOnSuccess", false);

        when(accessLogsRepository.existsByRequestId("req-1")).thenReturn(true);

        String payload = """
                {
                  \"schemaVersion\": \"access_log_event.v1\",
                  \"eventType\": \"ACCESS_LOG\",
                  \"eventSource\": \"enterprise-rag-community\",
                  \"eventId\": \"req-1\",
                  \"eventTime\": \"2026-04-17T10:10:10\",
                  \"data\": {
                    \"tenantId\": 1,
                    \"userId\": 2,
                    \"username\": \"u\",
                    \"method\": \"GET\",
                    \"path\": \"/p\",
                    \"statusCode\": 200,
                    "clientIp": "127.0.0.1",
                    \"requestId\": \"req-1\",
                    \"traceId\": \"trace-1\",
                    \"createdAt\": \"2026-04-17T10:10:10\"
                  }
                }
                """;

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        consumer.consume(payload, "k1", "access-logs-v1", acknowledgment);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Document> docCap = org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(esOps).save(docCap.capture(), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class));
        verify(accessLogsRepository).existsByRequestId("req-1");
        verify(acknowledgment).acknowledge();

        Document doc = docCap.getValue();
        assertEquals("req-1", doc.getId());
        assertEquals("req-1", doc.get("event_id"));
        assertEquals("/p", doc.get("path"));
        assertEquals("127.0.0.1", doc.get("client_ip"));
        assertEquals("127.0.0.1", doc.get("client_ip_text"));
    }

    @Test
      void consume_should_not_ack_when_payload_is_invalid_json() {
        ElasticsearchOperations esOps = mock(ElasticsearchOperations.class);
        AccessLogsRepository accessLogsRepository = mock(AccessLogsRepository.class);
        AccessLogEsSinkConsumer consumer = new AccessLogEsSinkConsumer(esOps, accessLogsRepository, new ObjectMapper());

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        assertThrows(IllegalStateException.class, () -> consumer.consume("{invalid-json", "k1", "access-logs-v1", acknowledgment));

        verify(esOps, never()).save(any(Document.class), any(org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.class));
        verify(acknowledgment, never()).acknowledge();
    }
}
