package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest;
import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunRequestDTO;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunDetailRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunHistoryRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.OpenSearchTokenizeService;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminLlmLoadTestServiceStreamTokenFallbackTest {

    @Test
    void streamTokensOut_missingCompletionTokens_fallsBackToTokenizer() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        AdminModerationLlmService adminModerationLlmService = mock(AdminModerationLlmService.class);
        LlmQueueMonitorService llmQueueMonitorService = mock(LlmQueueMonitorService.class);
        OpenSearchTokenizeService openSearchTokenizeService = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(openSearchTokenizeService);

        when(llmQueueMonitorService.query(any(), any(), any(), any())).thenReturn(null);

        CountDownLatch tokenizeTextCalled = new CountDownLatch(1);
        doAnswer(invocation -> {
            OpenSearchTokenizeRequest r = invocation.getArgument(0);
            OpenSearchTokenizeResponse resp = new OpenSearchTokenizeResponse();
            OpenSearchTokenizeResponse.Usage usage = new OpenSearchTokenizeResponse.Usage();
            if (r.getMessages() != null && !r.getMessages().isEmpty()) {
                usage.setInputTokens(10);
            } else if (r.getText() != null) {
                assertEquals("ok", r.getText());
                usage.setInputTokens(3);
                tokenizeTextCalled.countDown();
            } else {
                usage.setInputTokens(0);
            }
            resp.setUsage(usage);
            return resp;
        }).when(openSearchTokenizeService).tokenize(any(OpenSearchTokenizeRequest.class));

        doAnswer(invocation -> {
            LlmGateway.RoutedChatStreamResult r = new LlmGateway.RoutedChatStreamResult(
                    "mock-provider",
                    "mock-model",
                    new LlmCallQueueService.UsageMetrics(110, null, 133, 1227)
            );
            OpenAiCompatClient.SseLineConsumer consumer = invocation.getArgument(7);
            consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"<think>\\n\\n</think>\\n\\nok\"}}]}");
            consumer.onLine("data: [DONE]");
            return r;
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );

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
                llmLoadTestRunHistoryRepository
        );

        try {
            AdminLlmLoadTestRunRequestDTO runReq = new AdminLlmLoadTestRunRequestDTO();
            runReq.setConcurrency(1);
            runReq.setTotalRequests(1);
            runReq.setStream(true);
            runReq.setRatioChatStream(100);
            runReq.setRatioModerationTest(0);
            runReq.setTimeoutMs(5000);
            runReq.setRetries(0);
            runReq.setChatMessage("压测：请用一句话回复“ok”。");

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

            assertTrue(tokenizeTextCalled.await(2, TimeUnit.SECONDS));

            var st = svc.status(runId);
            assertNotNull(st);
            assertEquals(110L, st.getTokensInTotal());
            assertEquals(3L, st.getTokensOutTotal());
            assertEquals(113L, st.getTokensTotal());
        } finally {
            svc.shutdown();
        }
    }

    @Test
    void streamTokensOut_leadingNewlines_should_ignore_usage_and_use_tokenizer() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        AdminModerationLlmService adminModerationLlmService = mock(AdminModerationLlmService.class);
        LlmQueueMonitorService llmQueueMonitorService = mock(LlmQueueMonitorService.class);
        OpenSearchTokenizeService openSearchTokenizeService = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(openSearchTokenizeService);

        when(llmQueueMonitorService.query(any(), any(), any(), any())).thenReturn(null);

        CountDownLatch tokenizeTextCalled = new CountDownLatch(1);
        doAnswer(invocation -> {
            OpenSearchTokenizeRequest r = invocation.getArgument(0);
            OpenSearchTokenizeResponse resp = new OpenSearchTokenizeResponse();
            OpenSearchTokenizeResponse.Usage usage = new OpenSearchTokenizeResponse.Usage();
            if (r.getText() != null) {
                assertEquals("ok", r.getText());
                usage.setInputTokens(3);
                tokenizeTextCalled.countDown();
            } else if (r.getMessages() != null && !r.getMessages().isEmpty()) {
                usage.setInputTokens(10);
            } else {
                usage.setInputTokens(0);
            }
            resp.setUsage(usage);
            return resp;
        }).when(openSearchTokenizeService).tokenize(any(OpenSearchTokenizeRequest.class));

        doAnswer(invocation -> {
            LlmGateway.RoutedChatStreamResult r = new LlmGateway.RoutedChatStreamResult(
                    "mock-provider",
                    "mock-model",
                    new LlmCallQueueService.UsageMetrics(110, 6, 116, null)
            );
            OpenAiCompatClient.SseLineConsumer consumer = invocation.getArgument(7);
            consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"\\n\\nok\"}}]}");
            consumer.onLine("data: [DONE]");
            return r;
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );

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
                llmLoadTestRunHistoryRepository
        );

        try {
            AdminLlmLoadTestRunRequestDTO runReq = new AdminLlmLoadTestRunRequestDTO();
            runReq.setConcurrency(1);
            runReq.setTotalRequests(1);
            runReq.setStream(true);
            runReq.setRatioChatStream(100);
            runReq.setRatioModerationTest(0);
            runReq.setTimeoutMs(5000);
            runReq.setRetries(0);
            runReq.setChatMessage("压测：请用一句话回复“ok”。");

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

            assertTrue(tokenizeTextCalled.await(2, TimeUnit.SECONDS));

            var st = svc.status(runId);
            assertNotNull(st);
            assertEquals(110L, st.getTokensInTotal());
            assertEquals(3L, st.getTokensOutTotal());
            assertEquals(113L, st.getTokensTotal());
        } finally {
            svc.shutdown();
        }
    }

    @Test
    void streamTokensOut_nvidiaOk_should_prefer_tokenizer_over_usage() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        AdminModerationLlmService adminModerationLlmService = mock(AdminModerationLlmService.class);
        LlmQueueMonitorService llmQueueMonitorService = mock(LlmQueueMonitorService.class);
        OpenSearchTokenizeService openSearchTokenizeService = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(openSearchTokenizeService);

        when(llmQueueMonitorService.query(any(), any(), any(), any())).thenReturn(null);

        CountDownLatch tokenizeTextCalled = new CountDownLatch(1);
        doAnswer(invocation -> {
            OpenSearchTokenizeRequest r = invocation.getArgument(0);
            OpenSearchTokenizeResponse resp = new OpenSearchTokenizeResponse();
            OpenSearchTokenizeResponse.Usage usage = new OpenSearchTokenizeResponse.Usage();
            if (r.getText() != null) {
                assertEquals("ok", r.getText());
                usage.setInputTokens(1);
                tokenizeTextCalled.countDown();
            } else if (r.getMessages() != null && !r.getMessages().isEmpty()) {
                usage.setInputTokens(10);
            } else {
                usage.setInputTokens(0);
            }
            resp.setUsage(usage);
            return resp;
        }).when(openSearchTokenizeService).tokenize(any(OpenSearchTokenizeRequest.class));

        doAnswer(invocation -> {
            LlmGateway.RoutedChatStreamResult r = new LlmGateway.RoutedChatStreamResult(
                    "nvidia",
                    "mock-model",
                    new LlmCallQueueService.UsageMetrics(110, 2, 112, 1)
            );
            OpenAiCompatClient.SseLineConsumer consumer = invocation.getArgument(7);
            consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}");
            consumer.onLine("data: [DONE]");
            return r;
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );

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
                llmLoadTestRunHistoryRepository
        );

        try {
            AdminLlmLoadTestRunRequestDTO runReq = new AdminLlmLoadTestRunRequestDTO();
            runReq.setConcurrency(1);
            runReq.setTotalRequests(1);
            runReq.setStream(true);
            runReq.setRatioChatStream(100);
            runReq.setRatioModerationTest(0);
            runReq.setTimeoutMs(5000);
            runReq.setRetries(0);
            runReq.setChatMessage("压测：请用一句话回复“ok”。");

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

            assertTrue(tokenizeTextCalled.await(2, TimeUnit.SECONDS));

            var st = svc.status(runId);
            assertNotNull(st);
            assertEquals(110L, st.getTokensInTotal());
            assertEquals(1L, st.getTokensOutTotal());
            assertEquals(111L, st.getTokensTotal());
        } finally {
            svc.shutdown();
        }
    }

    @Test
    void streamTokensOut_hasCompletionTokens_but_thinkBlocks_should_ignore_usage_and_use_tokenizer() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        AdminModerationLlmService adminModerationLlmService = mock(AdminModerationLlmService.class);
        LlmQueueMonitorService llmQueueMonitorService = mock(LlmQueueMonitorService.class);
        OpenSearchTokenizeService openSearchTokenizeService = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(openSearchTokenizeService);

        when(llmQueueMonitorService.query(any(), any(), any(), any())).thenReturn(null);

        CountDownLatch tokenizeTextCalled = new CountDownLatch(1);
        doAnswer(invocation -> {
            OpenSearchTokenizeRequest r = invocation.getArgument(0);
            OpenSearchTokenizeResponse resp = new OpenSearchTokenizeResponse();
            OpenSearchTokenizeResponse.Usage usage = new OpenSearchTokenizeResponse.Usage();
            if (r.getMessages() != null && !r.getMessages().isEmpty()) {
                usage.setInputTokens(10);
            } else if (r.getText() != null) {
                assertEquals("ok", r.getText());
                usage.setInputTokens(3);
                tokenizeTextCalled.countDown();
            } else {
                usage.setInputTokens(0);
            }
            resp.setUsage(usage);
            return resp;
        }).when(openSearchTokenizeService).tokenize(any(OpenSearchTokenizeRequest.class));

        doAnswer(invocation -> {
            LlmGateway.RoutedChatStreamResult r = new LlmGateway.RoutedChatStreamResult(
                    "mock-provider",
                    "mock-model",
                    new LlmCallQueueService.UsageMetrics(110, 999, 110 + 999, null)
            );
            OpenAiCompatClient.SseLineConsumer consumer = invocation.getArgument(7);
            consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"<think>\\n\\n</think>\\n\\nok\"}}]}");
            consumer.onLine("data: [DONE]");
            return r;
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );

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
                llmLoadTestRunHistoryRepository
        );

        try {
            AdminLlmLoadTestRunRequestDTO runReq = new AdminLlmLoadTestRunRequestDTO();
            runReq.setConcurrency(1);
            runReq.setTotalRequests(1);
            runReq.setStream(true);
            runReq.setRatioChatStream(100);
            runReq.setRatioModerationTest(0);
            runReq.setTimeoutMs(5000);
            runReq.setRetries(0);
            runReq.setChatMessage("压测：请用一句话回复“ok”。");

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

            assertTrue(tokenizeTextCalled.await(2, TimeUnit.SECONDS));

            var st = svc.status(runId);
            assertNotNull(st);
            assertEquals(110L, st.getTokensInTotal());
            assertEquals(3L, st.getTokensOutTotal());
            assertEquals(113L, st.getTokensTotal());
        } finally {
            svc.shutdown();
        }
    }

    @Test
    void streamTokensOut_totalTokensLessThanTokenizerPrompt_shouldTreatTotalAsOutputTokens() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        AdminModerationLlmService adminModerationLlmService = mock(AdminModerationLlmService.class);
        LlmQueueMonitorService llmQueueMonitorService = mock(LlmQueueMonitorService.class);
        OpenSearchTokenizeService openSearchTokenizeService = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(openSearchTokenizeService);

        when(llmQueueMonitorService.query(any(), any(), any(), any())).thenReturn(null);

        CountDownLatch tokenizeMessagesCalled = new CountDownLatch(1);
        doAnswer(invocation -> {
            OpenSearchTokenizeRequest r = invocation.getArgument(0);
            OpenSearchTokenizeResponse resp = new OpenSearchTokenizeResponse();
            OpenSearchTokenizeResponse.Usage usage = new OpenSearchTokenizeResponse.Usage();
            if (r.getMessages() != null && !r.getMessages().isEmpty()) {
                usage.setInputTokens(332);
                tokenizeMessagesCalled.countDown();
            } else {
                usage.setInputTokens(0);
            }
            resp.setUsage(usage);
            return resp;
        }).when(openSearchTokenizeService).tokenize(any(OpenSearchTokenizeRequest.class));

        doAnswer(invocation -> {
            LlmGateway.RoutedChatStreamResult r = new LlmGateway.RoutedChatStreamResult(
                    "mock-provider",
                    "mock-model",
                    new LlmCallQueueService.UsageMetrics(null, 1, 38, null)
            );
            OpenAiCompatClient.SseLineConsumer consumer = invocation.getArgument(7);
            consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}");
            consumer.onLine("data: [DONE]");
            return r;
        }).when(llmGateway).chatStreamRouted(
                any(LlmQueueTaskType.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );

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
                llmLoadTestRunHistoryRepository
        );

        try {
            AdminLlmLoadTestRunRequestDTO runReq = new AdminLlmLoadTestRunRequestDTO();
            runReq.setConcurrency(1);
            runReq.setTotalRequests(1);
            runReq.setStream(true);
            runReq.setRatioChatStream(100);
            runReq.setRatioModerationTest(0);
            runReq.setTimeoutMs(5000);
            runReq.setRetries(0);
            runReq.setChatMessage("压测：请用一句话回复“ok”。");

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

            assertTrue(tokenizeMessagesCalled.await(2, TimeUnit.SECONDS));

            var st = svc.status(runId);
            assertNotNull(st);
            assertEquals(332L, st.getTokensInTotal());
            assertEquals(38L, st.getTokensOutTotal());
            assertEquals(370L, st.getTokensTotal());
        } finally {
            svc.shutdown();
        }
    }
}
