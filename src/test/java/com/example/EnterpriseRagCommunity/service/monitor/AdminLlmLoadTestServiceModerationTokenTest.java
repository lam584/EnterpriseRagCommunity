package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest;
import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunRequestDTO;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunDetailRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunHistoryRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.OpenSearchTokenizeService;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminLlmLoadTestServiceModerationTokenTest {

    @Test
    void moderationTokensIn_usesPromptMessagesIncludingSystemPrompt() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        AdminModerationLlmService adminModerationLlmService = mock(AdminModerationLlmService.class);
        LlmQueueMonitorService llmQueueMonitorService = mock(LlmQueueMonitorService.class);
        OpenSearchTokenizeService openSearchTokenizeService = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(openSearchTokenizeService);

        when(llmQueueMonitorService.query(any(), any(), any(), any())).thenReturn(null);

        String systemPrompt = "You are a content safety moderation assistant.";
        String userPrompt = "压测：这是一条中性内容，用于审核模型吞吐测试。 #1";

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity mockPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        mockPrompt.setSystemPrompt(systemPrompt);
        // We need to mock PromptsRepository later, but we can't do it before creating the mock object.
        // Moving mock creation up.


        LlmModerationTestResponse resp = new LlmModerationTestResponse();
        resp.setLatencyMs(1L);
        resp.setModel("mock-model");
        resp.setRawModelOutput("ok");
        LlmModerationTestResponse.Usage u = new LlmModerationTestResponse.Usage();
        u.setPromptTokens(1);
        u.setCompletionTokens(1);
        u.setTotalTokens(2);
        resp.setUsage(u);
        List<LlmModerationTestResponse.Message> promptMessages = new ArrayList<>();
        {
            LlmModerationTestResponse.Message m1 = new LlmModerationTestResponse.Message();
            m1.setRole("system");
            m1.setContent(systemPrompt);
            promptMessages.add(m1);
            LlmModerationTestResponse.Message m2 = new LlmModerationTestResponse.Message();
            m2.setRole("user");
            m2.setContent(userPrompt);
            promptMessages.add(m2);
        }
        resp.setPromptMessages(promptMessages);
        when(adminModerationLlmService.test(any(LlmModerationTestRequest.class))).thenReturn(resp);

        CountDownLatch tokenizeCalled = new CountDownLatch(1);
        AtomicReference<OpenSearchTokenizeRequest> capturedReq = new AtomicReference<>(null);
        doAnswer(invocation -> {
            OpenSearchTokenizeRequest r = invocation.getArgument(0);
            capturedReq.set(r);
            tokenizeCalled.countDown();
            OpenSearchTokenizeResponse out = new OpenSearchTokenizeResponse();
            OpenSearchTokenizeResponse.Usage uu = new OpenSearchTokenizeResponse.Usage();
            uu.setInputTokens(42);
            out.setUsage(uu);
            return out;
        }).when(openSearchTokenizeService).tokenize(any(OpenSearchTokenizeRequest.class));

        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        LlmPriceConfigRepository llmPriceConfigRepository = mock(LlmPriceConfigRepository.class);
        when(llmModelRepository.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(anyString(), any())).thenReturn(List.of());
        when(llmPriceConfigRepository.findByIdIn(any())).thenReturn(List.of());
        when(llmPriceConfigRepository.findByNameIn(any())).thenReturn(List.of());
        LlmLoadTestRunDetailRepository llmLoadTestRunDetailRepository = mock(LlmLoadTestRunDetailRepository.class);
        LlmLoadTestRunHistoryRepository llmLoadTestRunHistoryRepository = mock(LlmLoadTestRunHistoryRepository.class);
        com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository promptsRepository = mock(com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository.class);
        when(promptsRepository.findByPromptCode("PORTAL_CHAT_ASSISTANT")).thenReturn(java.util.Optional.of(mockPrompt));

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
                promptsRepository
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
            runReq.setModerationText("压测：这是一条中性内容，用于审核模型吞吐测试。");

            String runId = svc.start(runReq);
            assertNotNull(runId);

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                var st = svc.status(runId);
                if (st != null && Boolean.FALSE.equals(st.getRunning()) && st.getDone() != null && st.getDone() >= 1) {
                    break;
                }
                Thread.sleep(25);
            }

            assertTrue(tokenizeCalled.await(2, TimeUnit.SECONDS));

            OpenSearchTokenizeRequest captured = capturedReq.get();
            assertNotNull(captured);
            assertNotNull(captured.getMessages());
            assertEquals(2, captured.getMessages().size());
            assertEquals("system", captured.getMessages().get(0).getRole());
            assertEquals(systemPrompt, captured.getMessages().get(0).getContent());

            long totalsDeadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < totalsDeadline) {
                var st = svc.status(runId);
                if (st != null && st.getTokensInTotal() != null && st.getTokensInTotal() == 42L) {
                    break;
                }
                Thread.sleep(25);
            }

            var st = svc.status(runId);
            assertNotNull(st);
            assertEquals(42L, st.getTokensInTotal());
        } finally {
            svc.shutdown();
        }
    }
}
