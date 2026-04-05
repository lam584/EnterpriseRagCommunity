package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest;
import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunRequestDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueSampleDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueStatusDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmPriceConfigEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.LlmLoadTestRunDetailEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.LlmLoadTestRunHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunDetailRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.OpenSearchTokenizeService;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminLlmLoadTestServiceRunFlowBranchTest {

    @Test
    void start_should_cover_chat_moderation_status_and_export_paths() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        AdminModerationLlmService moderationSvc = mock(AdminModerationLlmService.class);
        LlmQueueMonitorService queueSvc = mock(LlmQueueMonitorService.class);
        OpenSearchTokenizeService tokenizeSvc = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(tokenizeSvc);

        doAnswer(inv -> {
            OpenSearchTokenizeRequest req = inv.getArgument(0);
            OpenSearchTokenizeResponse r = new OpenSearchTokenizeResponse();
            OpenSearchTokenizeResponse.Usage u = new OpenSearchTokenizeResponse.Usage();
            if (req.getMessages() != null && !req.getMessages().isEmpty()) u.setInputTokens(11);
            else u.setInputTokens(3);
            r.setUsage(u);
            return r;
        }).when(tokenizeSvc).tokenize(any(OpenSearchTokenizeRequest.class));

        AdminLlmQueueStatusDTO q = new AdminLlmQueueStatusDTO();
        q.setPendingCount(2);
        q.setRunningCount(1);
        AdminLlmQueueSampleDTO sample = new AdminLlmQueueSampleDTO();
        sample.setTokensPerSec(12.0);
        q.setSamples(List.of(sample));
        when(queueSvc.query(any(), any(), any(), any())).thenReturn(q);

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(7);
            consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult(
                    "mock-provider",
                    "qwen3-8b",
                    new LlmCallQueueService.UsageMetrics(100, null, 130, 1)
            );
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class), any(), any(), any(), any(), any(), any(), any()
        );

        doAnswer(inv -> {
            LlmModerationTestResponse resp = new LlmModerationTestResponse();
            resp.setLatencyMs(2L);
            resp.setModel("qwen3-8b");
            resp.setRawModelOutput("{\"choices\":[{\"message\":{\"content\":\"safe\"}}]}");
            LlmModerationTestResponse.Usage u = new LlmModerationTestResponse.Usage();
            u.setPromptTokens(5);
            u.setCompletionTokens(2);
            u.setTotalTokens(7);
            resp.setUsage(u);
            List<LlmModerationTestResponse.Message> pms = new ArrayList<>();
            LlmModerationTestResponse.Message m1 = new LlmModerationTestResponse.Message();
            m1.setRole("system");
            m1.setContent("sys");
            pms.add(m1);
            resp.setPromptMessages(pms);
            return resp;
        }).when(moderationSvc).test(any(LlmModerationTestRequest.class));

        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);
        LlmModelEntity me = new LlmModelEntity();
        me.setModelName("qwen3-8b");
        me.setPriceConfigId(1L);
        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), any())).thenReturn(List.of(me));
        LlmPriceConfigEntity pe = new LlmPriceConfigEntity();
        pe.setId(1L);
        pe.setName("qwen3-8b");
        pe.setCurrency("usd");
        pe.setInputCostPer1k(java.math.BigDecimal.ONE);
        pe.setOutputCostPer1k(java.math.BigDecimal.ONE);
        when(priceRepo.findByIdIn(any())).thenReturn(List.of(pe));
        when(priceRepo.findByNameIn(any())).thenReturn(List.of(pe));

        LlmLoadTestRunDetailRepository detailRepo = mock(LlmLoadTestRunDetailRepository.class);
        when(detailRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(detailRepo.existsByRunId(anyString())).thenReturn(true);
        LlmLoadTestRunDetailEntity d = new LlmLoadTestRunDetailEntity();
        d.setRunId("x");
        d.setReqIndex(0);
        d.setKind("CHAT_STREAM");
        d.setOk(true);
        d.setStartedAt(LocalDateTime.now());
        d.setFinishedAt(LocalDateTime.now());
        d.setRequestJson("{\"x\":1}");
        d.setResponseJson("{\"y\":2}");
        when(detailRepo.findByRunIdOrderByReqIndexAsc(anyString(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(d), PageRequest.of(0, 2000), 1));

        LlmLoadTestRunHistoryRepository historyRepo = mock(LlmLoadTestRunHistoryRepository.class);
        when(historyRepo.existsById(anyString())).thenReturn(true);
        LlmLoadTestRunHistoryEntity h = new LlmLoadTestRunHistoryEntity();
        h.setRunId("x");
        h.setSummaryJson("{\"runId\":\"x\"}");
        when(historyRepo.findById(anyString())).thenReturn(Optional.of(h));

        PromptsRepository promptsRepo = mock(PromptsRepository.class);
        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity prompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        prompt.setSystemPrompt("sys");
        when(promptsRepo.findByPromptCode("PORTAL_CHAT_ASSISTANT")).thenReturn(Optional.of(prompt));

        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                llmGateway, moderationSvc, queueSvc, tokenCountService, new ObjectMapper(),
                modelRepo, priceRepo, detailRepo, historyRepo, promptsRepo
        );

        try {
            AdminLlmLoadTestRunRequestDTO req = new AdminLlmLoadTestRunRequestDTO();
            req.setConcurrency(1);
            req.setTotalRequests(3);
            req.setStream(true);
            req.setRatioChatStream(50);
            req.setRatioModerationTest(50);
            req.setTimeoutMs(5000);
            req.setRetries(0);
            req.setEnableThinking(false);
            req.setModel("qwen3-8b");
            req.setChatMessage("hello");
            req.setModerationText("safe");

            String runId = svc.start(req);
            assertNotNull(runId);
            long deadline = System.currentTimeMillis() + 7000;
            while (System.currentTimeMillis() < deadline) {
                var st = svc.status(runId);
                if (st != null && Boolean.FALSE.equals(st.getRunning()) && st.getDone() != null && st.getDone() >= 3) {
                    break;
                }
                Thread.sleep(30);
            }
            var st = svc.status(runId);
            assertNotNull(st);
            assertEquals(3, st.getDone());
            assertTrue(st.getQueuePeak().getMaxTotal() >= 0);
            assertTrue(st.getTokensTotal() >= 0);

            ResponseEntity<StreamingResponseBody> jsonRes = svc.export(runId, false);
            assertEquals(200, jsonRes.getStatusCode().value());
            ByteArrayOutputStream outJson = new ByteArrayOutputStream();
            jsonRes.getBody().writeTo(outJson);
            assertTrue(outJson.toString(StandardCharsets.UTF_8).contains("details"));

            ResponseEntity<StreamingResponseBody> csvRes = svc.export(runId, true);
            assertEquals(200, csvRes.getStatusCode().value());
            ByteArrayOutputStream outCsv = new ByteArrayOutputStream();
            csvRes.getBody().writeTo(outCsv);
            assertTrue(outCsv.toString(StandardCharsets.UTF_8).contains("runId,index,kind"));
        } finally {
            svc.shutdown();
        }
    }

    @Test
    void start_should_cover_retry_timeout_and_stop_branches() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        AdminModerationLlmService moderationSvc = mock(AdminModerationLlmService.class);
        LlmQueueMonitorService queueSvc = mock(LlmQueueMonitorService.class);
        OpenSearchTokenizeService tokenizeSvc = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(tokenizeSvc);

        when(queueSvc.query(any(), any(), any(), any())).thenReturn(null);
        doAnswer(inv -> {
            OpenSearchTokenizeResponse r = new OpenSearchTokenizeResponse();
            OpenSearchTokenizeResponse.Usage u = new OpenSearchTokenizeResponse.Usage();
            u.setInputTokens(1);
            r.setUsage(u);
            return r;
        }).when(tokenizeSvc).tokenize(any(OpenSearchTokenizeRequest.class));

        AtomicInteger chatCalls = new AtomicInteger(0);
        doAnswer(inv -> {
            int n = chatCalls.incrementAndGet();
            if (n == 1) throw new RuntimeException("first fail");
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(7);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult(
                    "mock-provider",
                    "qwen3-8b",
                    new LlmCallQueueService.UsageMetrics(1, 1, 2, 1)
            );
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class), any(), any(), any(), any(), any(), any(), any()
        );

        CountDownLatch moderationCalled = new CountDownLatch(1);
        doAnswer(inv -> {
            moderationCalled.countDown();
            throw new RuntimeException("moderation fail");
        }).when(moderationSvc).test(any(LlmModerationTestRequest.class));

        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmPriceConfigRepository priceRepo = mock(LlmPriceConfigRepository.class);
        when(modelRepo.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), any())).thenReturn(List.of());
        when(priceRepo.findByIdIn(any())).thenReturn(List.of());
        when(priceRepo.findByNameIn(any())).thenReturn(List.of());
        LlmLoadTestRunDetailRepository detailRepo = mock(LlmLoadTestRunDetailRepository.class);
        when(detailRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(detailRepo.existsByRunId(anyString())).thenReturn(false);
        when(detailRepo.findByRunIdOrderByReqIndexAsc(anyString(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 2000), 0));
        LlmLoadTestRunHistoryRepository historyRepo = mock(LlmLoadTestRunHistoryRepository.class);
        when(historyRepo.existsById(anyString())).thenReturn(false);
        when(historyRepo.findById(anyString())).thenReturn(Optional.empty());
        PromptsRepository promptsRepo = mock(PromptsRepository.class);
        when(promptsRepo.findByPromptCode("PORTAL_CHAT_ASSISTANT")).thenReturn(Optional.empty());

        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                llmGateway, moderationSvc, queueSvc, tokenCountService, new ObjectMapper(),
                modelRepo, priceRepo, detailRepo, historyRepo, promptsRepo
        );

        try {
            AdminLlmLoadTestRunRequestDTO req = new AdminLlmLoadTestRunRequestDTO();
            req.setConcurrency(1);
            req.setTotalRequests(1);
            req.setStream(true);
            req.setRatioChatStream(100);
            req.setRatioModerationTest(0);
            req.setTimeoutMs(1000);
            req.setRetries(1);
            req.setRetryDelayMs(1);
            req.setModel("qwen3-8b");
            req.setChatMessage("x");
            req.setModerationText("y");

            String runId = svc.start(req);
            assertNotNull(runId);
            long deadline = System.currentTimeMillis() + 7000;
            while (System.currentTimeMillis() < deadline) {
                var st = svc.status(runId);
                if (st != null && Boolean.FALSE.equals(st.getRunning()) && st.getDone() != null && st.getDone() >= 1) {
                    break;
                }
                Thread.sleep(30);
            }
            var st = svc.status(runId);
            assertNotNull(st);
            assertEquals(1, st.getDone());
            assertTrue(chatCalls.get() >= 2);

            AdminLlmLoadTestRunRequestDTO req2 = new AdminLlmLoadTestRunRequestDTO();
            req2.setConcurrency(1);
            req2.setTotalRequests(1);
            req2.setStream(false);
            req2.setRatioChatStream(0);
            req2.setRatioModerationTest(100);
            req2.setTimeoutMs(1000);
            req2.setRetries(0);
            req2.setModel("qwen3-8b");
            req2.setModerationText("y");
            String runId2 = svc.start(req2);
            assertNotNull(runId2);
            long deadline2 = System.currentTimeMillis() + 7000;
            while (System.currentTimeMillis() < deadline2) {
                var st2 = svc.status(runId2);
                if (st2 != null && Boolean.FALSE.equals(st2.getRunning()) && st2.getDone() != null && st2.getDone() >= 1) {
                    break;
                }
                Thread.sleep(30);
            }
            assertTrue(moderationCalled.await(2, TimeUnit.SECONDS));
            var st2 = svc.status(runId2);
            assertNotNull(st2);
            assertEquals(1, st2.getDone());
            assertTrue(st2.getFailed() >= 1);

            assertFalse(svc.stop("not-exist"));
            assertTrue(svc.stop(runId));
            assertEquals(404, svc.export("none", false).getStatusCode().value());
        } finally {
            svc.shutdown();
        }
    }
}
