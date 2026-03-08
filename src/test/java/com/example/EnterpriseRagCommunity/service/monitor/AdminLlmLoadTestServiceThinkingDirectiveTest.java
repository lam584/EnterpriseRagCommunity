package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunRequestDTO;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunDetailRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.OpenSearchTokenizeService;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminLlmLoadTestServiceThinkingDirectiveTest {

    @Test
    void moderationTest_should_append_no_think_for_qwen3_when_enableThinking_is_false() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        AdminModerationLlmService adminModerationLlmService = mock(AdminModerationLlmService.class);
        LlmQueueMonitorService llmQueueMonitorService = mock(LlmQueueMonitorService.class);
        OpenSearchTokenizeService openSearchTokenizeService = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(openSearchTokenizeService);

        when(llmQueueMonitorService.query(any(), any(), any(), any())).thenReturn(null);

        CountDownLatch called = new CountDownLatch(1);
        AtomicReference<LlmModerationTestRequest> captured = new AtomicReference<>(null);
        doAnswer(invocation -> {
            LlmModerationTestRequest req = invocation.getArgument(0);
            captured.set(req);
            called.countDown();
            LlmModerationTestResponse resp = new LlmModerationTestResponse();
            resp.setLatencyMs(1L);
            resp.setModel("qwen3-8b");
            resp.setRawModelOutput("{\"decision\":\"APPROVE\"}");
            return resp;
        }).when(adminModerationLlmService).test(any(LlmModerationTestRequest.class));

        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        LlmPriceConfigRepository llmPriceConfigRepository = mock(LlmPriceConfigRepository.class);
        when(llmModelRepository.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), any())).thenReturn(List.of());
        when(llmPriceConfigRepository.findByIdIn(any())).thenReturn(List.of());
        when(llmPriceConfigRepository.findByNameIn(any())).thenReturn(List.of());
        LlmLoadTestRunDetailRepository llmLoadTestRunDetailRepository = mock(LlmLoadTestRunDetailRepository.class);
        LlmLoadTestRunHistoryRepository llmLoadTestRunHistoryRepository = mock(LlmLoadTestRunHistoryRepository.class);

        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                llmGateway,
                adminModerationLlmService,
                llmQueueMonitorService,
                tokenCountService,
                new ObjectMapper(),
                llmModelRepository,
                llmPriceConfigRepository,
                llmLoadTestRunDetailRepository,
                llmLoadTestRunHistoryRepository,
                mock(PromptsRepository.class)
        );

        try {
            AdminLlmLoadTestRunRequestDTO runReq = new AdminLlmLoadTestRunRequestDTO();
            runReq.setConcurrency(1);
            runReq.setTotalRequests(1);
            runReq.setStream(false);
            runReq.setRatioChatStream(0);
            runReq.setRatioModerationTest(100);
            runReq.setTimeoutMs(5000);
            runReq.setRetries(0);
            runReq.setEnableThinking(false);
            runReq.setModel("qwen3-8b");
            runReq.setModerationText("压测：这是一条中性内容，用于审核模型吞吐测试。");

            String runId = svc.start(runReq);
            assertNotNull(runId);

            assertTrue(called.await(3, TimeUnit.SECONDS));

            LlmModerationTestRequest req = captured.get();
            assertNotNull(req);
            assertTrue(String.valueOf(req.getText()).contains("/no_think"));
        } finally {
            svc.shutdown();
        }
    }

    @Test
    void moderationTest_should_append_think_for_qwen3_when_enableThinking_is_true() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        AdminModerationLlmService adminModerationLlmService = mock(AdminModerationLlmService.class);
        LlmQueueMonitorService llmQueueMonitorService = mock(LlmQueueMonitorService.class);
        OpenSearchTokenizeService openSearchTokenizeService = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(openSearchTokenizeService);

        when(llmQueueMonitorService.query(any(), any(), any(), any())).thenReturn(null);

        CountDownLatch called = new CountDownLatch(1);
        AtomicReference<LlmModerationTestRequest> captured = new AtomicReference<>(null);
        doAnswer(invocation -> {
            LlmModerationTestRequest req = invocation.getArgument(0);
            captured.set(req);
            called.countDown();
            LlmModerationTestResponse resp = new LlmModerationTestResponse();
            resp.setLatencyMs(1L);
            resp.setModel("qwen3-8b");
            resp.setRawModelOutput("{\"decision\":\"APPROVE\"}");
            return resp;
        }).when(adminModerationLlmService).test(any(LlmModerationTestRequest.class));

        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        LlmPriceConfigRepository llmPriceConfigRepository = mock(LlmPriceConfigRepository.class);
        when(llmModelRepository.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), any())).thenReturn(List.of());
        when(llmPriceConfigRepository.findByIdIn(any())).thenReturn(List.of());
        when(llmPriceConfigRepository.findByNameIn(any())).thenReturn(List.of());
        LlmLoadTestRunDetailRepository llmLoadTestRunDetailRepository = mock(LlmLoadTestRunDetailRepository.class);
        LlmLoadTestRunHistoryRepository llmLoadTestRunHistoryRepository = mock(LlmLoadTestRunHistoryRepository.class);

        AdminLlmLoadTestService svc = new AdminLlmLoadTestService(
                llmGateway,
                adminModerationLlmService,
                llmQueueMonitorService,
                tokenCountService,
                new ObjectMapper(),
                llmModelRepository,
                llmPriceConfigRepository,
                llmLoadTestRunDetailRepository,
                llmLoadTestRunHistoryRepository,
                mock(PromptsRepository.class)
        );

        try {
            AdminLlmLoadTestRunRequestDTO runReq = new AdminLlmLoadTestRunRequestDTO();
            runReq.setConcurrency(1);
            runReq.setTotalRequests(1);
            runReq.setStream(false);
            runReq.setRatioChatStream(0);
            runReq.setRatioModerationTest(100);
            runReq.setTimeoutMs(5000);
            runReq.setRetries(0);
            runReq.setEnableThinking(true);
            runReq.setModel("qwen3-8b");
            runReq.setModerationText("压测：这是一条中性内容，用于审核模型吞吐测试。");

            String runId = svc.start(runReq);
            assertNotNull(runId);

            assertTrue(called.await(3, TimeUnit.SECONDS));

            LlmModerationTestRequest req = captured.get();
            assertNotNull(req);
            assertTrue(String.valueOf(req.getText()).contains("/think"));
        } finally {
            svc.shutdown();
        }
    }
}
