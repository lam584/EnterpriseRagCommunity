package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.LlmLoadTestRunDetailEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.LlmLoadTestRunHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunDetailRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminLlmLoadTestExportTest {

    @Test
    void exportJson_containsSummaryAndPerCallRequestResponse() throws Exception {
        String runId = "run-1";

        LlmLoadTestRunDetailRepository detailRepo = mock(LlmLoadTestRunDetailRepository.class);
        LlmLoadTestRunHistoryRepository historyRepo = mock(LlmLoadTestRunHistoryRepository.class);

        LlmLoadTestRunDetailEntity d = new LlmLoadTestRunDetailEntity();
        d.setId(1L);
        d.setRunId(runId);
        d.setReqIndex(0);
        d.setKind("CHAT_STREAM");
        d.setOk(true);
        d.setStartedAt(LocalDateTime.now());
        d.setFinishedAt(LocalDateTime.now());
        d.setLatencyMs(12L);
        d.setProviderId("p1");
        d.setModel("m1");
        d.setTokensIn(3);
        d.setTokensOut(2);
        d.setTotalTokens(5);
        d.setError(null);
        d.setRequestJson("{\"kind\":\"CHAT_STREAM\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        d.setResponseJson("{\"assistantText\":\"ok\"}");
        d.setRequestTruncated(false);
        d.setResponseTruncated(false);
        d.setCreatedAt(LocalDateTime.now());

        when(detailRepo.existsByRunId(runId)).thenReturn(true);
        when(detailRepo.findByRunIdOrderByReqIndexAsc(runId, PageRequest.of(0, 2000)))
                .thenReturn(new PageImpl<>(List.of(d), PageRequest.of(0, 2000), 1));

        when(historyRepo.existsById(runId)).thenReturn(true);
        LlmLoadTestRunHistoryEntity h = new LlmLoadTestRunHistoryEntity();
        h.setRunId(runId);
        h.setSummaryJson("{\"runId\":\"run-1\",\"config\":{\"concurrency\":1}}");
        when(historyRepo.findById(runId)).thenReturn(Optional.of(h));

        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                mock(LlmGateway.class),
                mock(AdminModerationLlmService.class),
                mock(LlmQueueMonitorService.class),
                mock(TokenCountService.class),
                new ObjectMapper(),
                mock(LlmModelRepository.class),
                mock(LlmPriceConfigRepository.class),
                detailRepo,
                historyRepo,
                mock(PromptsRepository.class)
        );

        ResponseEntity<StreamingResponseBody> res = svc.export(runId, "json");
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        res.getBody().writeTo(out);
        String json = out.toString(StandardCharsets.UTF_8);

        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(json);
        assertEquals("run-1", root.get("summary").get("runId").asText());
        assertEquals(1, root.get("details").size());
        assertEquals("CHAT_STREAM", root.get("details").get(0).get("kind").asText());
        assertEquals("hi", root.get("details").get(0).get("request").get("messages").get(0).get("content").asText());
        assertEquals("ok", root.get("details").get(0).get("response").get("assistantText").asText());
    }

    @Test
    void exportCsv_writesUtf8BomAndKeepsChineseReadable() throws Exception {
        String runId = "run-2";

        LlmLoadTestRunDetailRepository detailRepo = mock(LlmLoadTestRunDetailRepository.class);
        LlmLoadTestRunHistoryRepository historyRepo = mock(LlmLoadTestRunHistoryRepository.class);

        LlmLoadTestRunDetailEntity d = new LlmLoadTestRunDetailEntity();
        d.setId(1L);
        d.setRunId(runId);
        d.setReqIndex(0);
        d.setKind("CHAT_STREAM");
        d.setOk(true);
        d.setStartedAt(LocalDateTime.now());
        d.setFinishedAt(LocalDateTime.now());
        d.setLatencyMs(12L);
        d.setProviderId("p1");
        d.setModel("m1");
        d.setTokensIn(3);
        d.setTokensOut(2);
        d.setTotalTokens(5);
        d.setError(null);
        d.setRequestJson("{\"kind\":\"CHAT_STREAM\",\"messages\":[{\"role\":\"user\",\"content\":\"你好，世界\"}]}");
        d.setResponseJson("{\"assistantText\":\"好的\"}");
        d.setRequestTruncated(false);
        d.setResponseTruncated(false);
        d.setCreatedAt(LocalDateTime.now());

        when(detailRepo.existsByRunId(runId)).thenReturn(true);
        when(detailRepo.findByRunIdOrderByReqIndexAsc(runId, PageRequest.of(0, 2000)))
                .thenReturn(new PageImpl<>(List.of(d), PageRequest.of(0, 2000), 1));

        when(historyRepo.existsById(runId)).thenReturn(true);
        when(historyRepo.findById(runId)).thenReturn(Optional.empty());

        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                mock(LlmGateway.class),
                mock(AdminModerationLlmService.class),
                mock(LlmQueueMonitorService.class),
                mock(TokenCountService.class),
                new ObjectMapper(),
                mock(LlmModelRepository.class),
                mock(LlmPriceConfigRepository.class),
                detailRepo,
                historyRepo,
                mock(PromptsRepository.class)
        );

        ResponseEntity<StreamingResponseBody> res = svc.export(runId, "csv");
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        res.getBody().writeTo(out);
        byte[] bytes = out.toByteArray();

        assertTrue(bytes.length >= 3);
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);

        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFrunId,index,kind"));
        assertTrue(csv.contains("你好，世界"));
        assertTrue(csv.contains("好的"));
    }
}
