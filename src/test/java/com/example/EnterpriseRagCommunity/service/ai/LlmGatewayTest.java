package com.example.EnterpriseRagCommunity.service.ai;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.EnterpriseRagCommunity.service.ai.AiProvidersConfigService.ResolvedProvider;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;

class LlmGatewayTest {

    private static ResolvedProvider provider(String id, String type, String defaultChatModel) {
        return new ResolvedProvider(
                id,
                type,
                "http://example.invalid",
                "k",
                defaultChatModel,
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
    }

    private static AiEmbeddingService.EmbeddingResult dummyEmbedding(String model) {
        return new AiEmbeddingService.EmbeddingResult(new float[] { 1.0f, 2.0f }, 2, model);
    }

    private static AiRerankService.RerankResult dummyRerank(String providerId, String model) {
        return new AiRerankService.RerankResult(List.of(new AiRerankService.RerankHit(0, 0.9)), 10, providerId, model);
    }

    @Test
    void chatOnceRouted_should_use_modelOverride_when_provided_and_return_raw_response() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider provider = new ResolvedProvider(
                "p1",
                "OPENAI_COMPAT",
                "http://example.invalid",
                "k",
                "m-default",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider);

        String rawJson = """
                {"choices":[{"message":{"content":"hello"}}],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}
                """.trim();

        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenReturn(rawJson);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 3, 2));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        LlmGateway.RoutedChatOnceResult res = gateway.chatOnceRouted(
                LlmQueueTaskType.TEXT_CHAT,
                "p1",
                "m-override",
                List.of(ChatMessage.user("hi")),
                0.6
        );

        assertEquals(rawJson, res.text());
        assertEquals("p1", res.providerId());
        assertEquals("m-override", res.model());
        assertNotNull(res.usage());
        assertEquals(3, res.usage().totalTokens());

        verify(llmRoutingService, never()).pickNext(any(), any());
        verify(llmRoutingTelemetryService, never()).record(any());
    }

    @Test
    void chatOnceRouted_should_throw_when_provider_type_unsupported() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider provider = new ResolvedProvider(
                "p1",
                "DASHSCOPE",
                "http://example.invalid",
                "k",
                "m-default",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> gateway.chatOnceRouted(LlmQueueTaskType.TEXT_CHAT, "p1", null, List.of(ChatMessage.user("hi")), 0.6)
        );
        assertTrue(ex.getMessage().contains("暂不支持的模型提供商类型"));
    }

    @Test
    void chatOnceRouted_should_fallback_when_routed_call_retriable_fails_then_succeed() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        LlmRoutingService.Policy policy = new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500);
        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT)).thenReturn(policy);

        LlmRoutingService.RouteTarget target = new LlmRoutingService.RouteTarget(
                new LlmRoutingService.TargetId("p-route", "m-route"),
                1,
                1,
                null
        );
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(target);

        ResolvedProvider routedProvider = new ResolvedProvider(
                "p-route",
                "OPENAI_COMPAT",
                "http://example.invalid",
                "k",
                "m-default",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        ResolvedProvider fallbackProvider = new ResolvedProvider(
                "p-fallback",
                "OPENAI_COMPAT",
                "http://example.invalid",
                "k",
                "m-fallback",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p-route")).thenReturn(routedProvider);
        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("p-fallback"));
        when(aiProvidersConfigService.resolveProvider("p-fallback")).thenReturn(fallbackProvider);

        String okRawJson = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.trim();

        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        ))
                .thenThrow(new SocketTimeoutException("timeout"))
                .thenReturn(okRawJson);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        LlmGateway.RoutedChatOnceResult res = gateway.chatOnceRouted(
                LlmQueueTaskType.TEXT_CHAT,
                null,
                null,
                List.of(ChatMessage.user("hi")),
                0.1
        );

        assertEquals(okRawJson, res.text());
        assertEquals("p-fallback", res.providerId());
        assertEquals("m-fallback", res.model());

        verify(llmRoutingService, times(1)).recordFailure(eq(LlmQueueTaskType.TEXT_CHAT), eq(target), any());
        verify(llmRoutingService, never()).recordSuccess(eq(LlmQueueTaskType.TEXT_CHAT), any());

        ArgumentCaptor<LlmRoutingTelemetryService.RoutingDecisionEvent> events = ArgumentCaptor.forClass(
                LlmRoutingTelemetryService.RoutingDecisionEvent.class
        );
        verify(llmRoutingTelemetryService, atLeastOnce()).record(events.capture());
        assertTrue(events.getAllValues().stream().anyMatch(e -> "ROUTE_FAIL".equals(e.kind())));
        assertTrue(events.getAllValues().stream().anyMatch(e -> "FALLBACK_OK".equals(e.kind())));
    }

    @Test
    void chatOnceRouted_should_throw_immediately_when_routed_call_non_retriable_fails() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        LlmRoutingService.Policy policy = new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 3, 1, 500);
        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT)).thenReturn(policy);

        LlmRoutingService.RouteTarget target = new LlmRoutingService.RouteTarget(
                new LlmRoutingService.TargetId("p-route", "m-route"),
                1,
                1,
                null
        );
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(target);

        ResolvedProvider routedProvider = new ResolvedProvider(
                "p-route",
                "OPENAI_COMPAT",
                "http://example.invalid",
                "k",
                "m-default",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p-route")).thenReturn(routedProvider);

        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        ))
                .thenThrow(new IllegalArgumentException("boom"));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> gateway.chatOnceRouted(LlmQueueTaskType.TEXT_CHAT, null, null, List.of(ChatMessage.user("hi")), 0.1)
        );
        assertTrue(ex.getMessage().contains("上游AI调用失败"));

        verify(llmRoutingService, never()).recordFailure(eq(LlmQueueTaskType.TEXT_CHAT), eq(target), any());
        verify(llmRoutingService, never()).recordSuccess(eq(LlmQueueTaskType.TEXT_CHAT), any());
        verify(llmRoutingTelemetryService, atLeastOnce()).record(any());
    }

    @Test
    void chatOnceRouted_should_handle_empty_raw_response_and_estimate_usage() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider provider = new ResolvedProvider(
                "p1",
                "OPENAI_COMPAT",
                "http://example.invalid",
                "k",
                "m-default",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider);

        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenReturn("");
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString())).thenReturn(null);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        LlmGateway.RoutedChatOnceResult res = gateway.chatOnceRouted(
                LlmQueueTaskType.TEXT_CHAT,
                "p1",
                null,
                List.of(ChatMessage.user("hi")),
                0.5
        );

        assertEquals("", res.text());
        assertEquals("p1", res.providerId());
        assertEquals("m-default", res.model());
        assertNotNull(res.usage());
        assertNotNull(res.usage().promptTokens());
        assertNotNull(res.usage().completionTokens());
        assertNotNull(res.usage().totalTokens());
        assertTrue(res.usage().totalTokens() >= res.usage().promptTokens());
    }

    @Test
    void chatOnceRouted_should_strip_think_blocks_when_enableThinking_false() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider provider = new ResolvedProvider(
                "p1",
                "OPENAI_COMPAT",
                "http://example.invalid",
                "k",
                "m-default",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider);

        String rawJson = """
                {"choices":[{"message":{"content":"<think>hidden</think>Hello <reasoning>r</reasoning>World"}}],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}
                """.trim();

        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 3, 2));

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("t1");
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<String> sup = (LlmCallQueueService.CheckedTaskSupplier<String>) inv.getArgument(4);
            return sup.get(task);
        });

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> when(mock.chatCompletionsOnce(any())).thenReturn(rawJson)
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            LlmGateway.RoutedChatOnceResult res = gateway.chatOnceRouted(
                    LlmQueueTaskType.TEXT_CHAT,
                    "p1",
                    null,
                    List.of(ChatMessage.user("hi")),
                    0.6,
                    null,
                    null,
                    null,
                    Boolean.FALSE
            );

            assertEquals(rawJson, res.text());
            verify(task, atLeastOnce()).reportOutput(anyString());
            ArgumentCaptor<String> out = ArgumentCaptor.forClass(String.class);
            verify(task, atLeastOnce()).reportOutput(out.capture());
            String reported = out.getValue();
            assertTrue(reported.contains("Hello"));
            assertTrue(reported.contains("World"));
            assertTrue(!reported.contains("<think>"));
            assertTrue(!reported.contains("hidden"));
        }
    }

    @Test
    void chatOnceRouted_should_inject_think_directive_for_supported_model() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider provider = new ResolvedProvider(
                "p1",
                "OPENAI_COMPAT",
                "http://example.invalid",
                "k",
                "qwen3-32b",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider);

        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1));

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("t1");
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<String> sup = (LlmCallQueueService.CheckedTaskSupplier<String>) inv.getArgument(4);
            return sup.get(task);
        });

        String rawJson = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.trim();

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> when(mock.chatCompletionsOnce(any())).thenReturn(rawJson)
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            List<ChatMessage> messages = List.of(
                    ChatMessage.system("sys"),
                    ChatMessage.user("hi")
            );

            gateway.chatOnceRouted(
                    LlmQueueTaskType.TEXT_CHAT,
                    "p1",
                    null,
                    messages,
                    0.6,
                    null,
                    null,
                    null,
                    Boolean.TRUE
            );

            OpenAiCompatClient client = mocked.constructed().get(0);
            ArgumentCaptor<OpenAiCompatClient.ChatRequest> reqCap = ArgumentCaptor.forClass(OpenAiCompatClient.ChatRequest.class);
            verify(client).chatCompletionsOnce(reqCap.capture());
            List<ChatMessage> sent = reqCap.getValue().messages();
            assertTrue(String.valueOf(sent.get(sent.size() - 1).content()).contains("/think"));
        }
    }

    @Test
    void chatStreamRouted_should_continue_routing_when_stream_not_started_and_retriable_fails() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        LlmRoutingService.Policy policy = new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 2, 1, 500);
        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT)).thenReturn(policy);

        LlmRoutingService.RouteTarget t1 = new LlmRoutingService.RouteTarget(
                new LlmRoutingService.TargetId("p1", "m1"),
                1,
                1,
                null
        );
        LlmRoutingService.RouteTarget t2 = new LlmRoutingService.RouteTarget(
                new LlmRoutingService.TargetId("p2", "m2"),
                1,
                1,
                null
        );
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(t1, t2);

        ResolvedProvider p1 = new ResolvedProvider(
                "p1",
                "OPENAI_COMPAT",
                "http://example.invalid",
                "k",
                "m-default",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        ResolvedProvider p2 = new ResolvedProvider(
                "p2",
                "OPENAI_COMPAT",
                "http://example.invalid",
                "k",
                "m-default",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p1);
        when(aiProvidersConfigService.resolveProvider("p2")).thenReturn(p2);

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("t1");
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> sup =
                    (LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>) inv.getArgument(4);
            return sup.get(task);
        });

        OpenAiCompatClient.SseLineConsumer consumer = mock(OpenAiCompatClient.SseLineConsumer.class);

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> Mockito.doAnswer(inv -> {
                    OpenAiCompatClient.ChatRequest req = inv.getArgument(0);
                    OpenAiCompatClient.SseLineConsumer c = inv.getArgument(1);
                    if ("m1".equals(req.model())) {
                        throw new SocketTimeoutException("timeout");
                    }
                    c.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}");
                    c.onLine("data: [DONE]");
                    return null;
                }).when(mock).chatCompletionsStream(any(), any())
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            LlmGateway.RoutedChatStreamResult res = gateway.chatStreamRouted(
                    LlmQueueTaskType.TEXT_CHAT,
                    null,
                    null,
                    List.of(ChatMessage.user("hi")),
                    0.2,
                    null,
                    null,
                    null,
                    consumer
            );

            assertEquals("p2", res.providerId());
            assertEquals("m2", res.model());
            verify(llmRoutingService, times(1)).recordFailure(eq(LlmQueueTaskType.TEXT_CHAT), eq(t1), any());
            verify(llmRoutingService, times(1)).recordSuccess(eq(LlmQueueTaskType.TEXT_CHAT), eq(t2));
            verify(llmRoutingTelemetryService, atLeastOnce()).record(any());
        }
    }

    @Test
    void chatStreamRouted_should_fail_immediately_when_stream_started_then_error() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        LlmRoutingService.Policy policy = new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500);
        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT)).thenReturn(policy);

        LlmRoutingService.RouteTarget t1 = new LlmRoutingService.RouteTarget(
                new LlmRoutingService.TargetId("p1", "m1"),
                1,
                1,
                null
        );
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(t1);

        ResolvedProvider p1 = new ResolvedProvider(
                "p1",
                "OPENAI_COMPAT",
                "http://example.invalid",
                "k",
                "m-default",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p1);

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("t1");
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> sup =
                    (LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>) inv.getArgument(4);
            return sup.get(task);
        });

        OpenAiCompatClient.SseLineConsumer consumer = mock(OpenAiCompatClient.SseLineConsumer.class);

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> Mockito.doAnswer(inv -> {
                    OpenAiCompatClient.SseLineConsumer c = inv.getArgument(1);
                    c.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}");
                    throw new SocketTimeoutException("timeout");
                }).when(mock).chatCompletionsStream(any(), any())
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.chatStreamRouted(
                            LlmQueueTaskType.TEXT_CHAT,
                            null,
                            null,
                            List.of(ChatMessage.user("hi")),
                            0.2,
                            null,
                            null,
                            null,
                            consumer
                    )
            );
            assertTrue(ex.getMessage().contains("上游AI流式调用失败"));
            verify(llmRoutingService, never()).recordFailure(eq(LlmQueueTaskType.TEXT_CHAT), eq(t1), any());
        }
    }

    @Test
    void embedOnceRouted_should_throw_provider_only_no_target_message() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        LlmRoutingService.Policy policy = new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500);
        when(llmRoutingService.getPolicy(LlmQueueTaskType.EMBEDDING)).thenReturn(policy);
        when(llmRoutingService.pickNextInProvider(eq(LlmQueueTaskType.EMBEDDING), eq("p1"), any())).thenReturn(null);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        Exception ex = assertThrows(
                Exception.class,
                () -> gateway.embedOnceRouted(LlmQueueTaskType.EMBEDDING, "p1", null, "x")
        );
        assertTrue(ex.getMessage().contains("providerId=p1"));
        verify(llmRoutingTelemetryService, atLeastOnce()).record(any());
    }

    @Test
    void rerankOnceRouted_should_wrap_non_retriable_exception_as_ioexception() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        LlmRoutingService.Policy policy = new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500);
        when(llmRoutingService.getPolicy(LlmQueueTaskType.RERANK)).thenReturn(policy);
        LlmRoutingService.RouteTarget t1 = new LlmRoutingService.RouteTarget(
                new LlmRoutingService.TargetId("p1", "m1"),
                1,
                1,
                null
        );
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.RERANK), any())).thenReturn(t1);
        when(aiRerankService.rerankOnce(eq("p1"), eq("m1"), anyString(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("boom"));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        Exception ex = assertThrows(
                Exception.class,
                () -> gateway.rerankOnceRouted(LlmQueueTaskType.RERANK, null, null, "q", List.of("d1"), 1, null, null, null)
        );
        assertTrue(ex.getMessage().contains("Rerank failed"));
        verify(llmRoutingTelemetryService, atLeastOnce()).record(any());
    }

    @Test
    void resolve_should_fallback_to_active_provider_when_resolve_provider_returns_null() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider active = new ResolvedProvider(
                "p-active",
                "OPENAI_COMPAT",
                "http://example.invalid",
                "k",
                "m-default",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p-missing")).thenReturn(null);
        when(aiProvidersConfigService.resolveActiveProvider()).thenReturn(active);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        ResolvedProvider res = gateway.resolve("p-missing");
        assertEquals("p-active", res.id());
    }

    @Test
    void chatOnce_should_delegate_to_chatOnceRouted_and_return_text() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        LlmGateway raw = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );
        LlmGateway gateway = Mockito.spy(raw);
        Mockito.doReturn(new LlmGateway.RoutedChatOnceResult("x", "p1", "m1", null))
                .when(gateway)
                .chatOnceRouted(eq(LlmQueueTaskType.MULTIMODAL_CHAT), eq("p1"), eq(null), any(), eq(0.6));

        String res = gateway.chatOnce("p1", null, List.of(ChatMessage.user("hi")), 0.6);
        assertEquals("x", res);
    }

    @Test
    void chatOnceRouted_should_record_ROUTE_NO_TARGET_then_fallback_ok_when_no_route_target() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(null);

        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("p-fallback"));
        when(aiProvidersConfigService.resolveProvider("p-fallback")).thenReturn(provider("p-fallback", "OPENAI_COMPAT", "m-fallback"));

        String okRawJson = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.trim();
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenReturn(okRawJson);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        LlmGateway.RoutedChatOnceResult res = gateway.chatOnceRouted(
                LlmQueueTaskType.TEXT_CHAT,
                null,
                null,
                List.of(ChatMessage.user("hi")),
                0.1
        );

        assertEquals(okRawJson, res.text());
        assertEquals("p-fallback", res.providerId());
        assertEquals("m-fallback", res.model());

        ArgumentCaptor<LlmRoutingTelemetryService.RoutingDecisionEvent> events =
                ArgumentCaptor.forClass(LlmRoutingTelemetryService.RoutingDecisionEvent.class);
        verify(llmRoutingTelemetryService, atLeastOnce()).record(events.capture());
        assertTrue(events.getAllValues().stream().anyMatch(e -> "ROUTE_NO_TARGET".equals(e.kind())));
        assertTrue(events.getAllValues().stream().anyMatch(e -> "FALLBACK_OK".equals(e.kind())));
    }

    @Test
    void chatOnceRouted_should_throw_no_route_target_for_IMAGE_CHAT_without_fallback() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.MULTIMODAL_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.MULTIMODAL_CHAT), any())).thenReturn(null);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> gateway.chatOnceRouted(LlmQueueTaskType.MULTIMODAL_CHAT, null, null, List.of(ChatMessage.user("hi")), 0.1)
        );
        assertTrue(ex.getMessage().contains("路由目标"));
        verify(aiProvidersConfigService, never()).listEnabledProviderIds();
    }

    @Test
    void chatOnceRouted_should_fallback_to_active_provider_when_enabled_list_empty() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(null);

        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of());
        when(aiProvidersConfigService.resolveActiveProvider()).thenReturn(provider("p-active", "OPENAI_COMPAT", "m-active"));

        String okRawJson = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.trim();
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenReturn(okRawJson);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        LlmGateway.RoutedChatOnceResult res = gateway.chatOnceRouted(
                LlmQueueTaskType.TEXT_CHAT,
                null,
                null,
                List.of(ChatMessage.user("hi")),
                0.1
        );
        assertEquals("p-active", res.providerId());
        assertEquals("m-active", res.model());
    }

    @Test
    void chatOnceRoutedNoQueue_should_keep_extra_body_for_dashscope_and_disable_thinking_flag() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider provider = new ResolvedProvider(
                "p-dash",
                "OPENAI_COMPAT",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "k",
                "qwen-max",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p-dash")).thenReturn(provider);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1));

        String rawJson = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.trim();

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> when(mock.chatCompletionsOnce(any())).thenReturn(rawJson)
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            gateway.chatOnceRoutedNoQueue(
                    LlmQueueTaskType.TEXT_CHAT,
                    "p-dash",
                    null,
                    List.of(ChatMessage.user("hi")),
                    0.2,
                    null,
                    null,
                    null,
                    Boolean.TRUE,
                    128,
                    Map.of("vl_high_resolution_images", true, "foo", "bar")
            );

            OpenAiCompatClient client = mocked.constructed().get(0);
            ArgumentCaptor<OpenAiCompatClient.ChatRequest> reqCap = ArgumentCaptor.forClass(OpenAiCompatClient.ChatRequest.class);
            verify(client).chatCompletionsOnce(reqCap.capture());
            OpenAiCompatClient.ChatRequest req = reqCap.getValue();
            assertEquals(Boolean.FALSE, req.enableThinking());
            assertEquals(null, req.thinkingBudget());
            assertEquals(true, req.extraBody().containsKey("vl_high_resolution_images"));
            assertEquals("bar", req.extraBody().get("foo"));
        }
    }

    @Test
    void chatOnceRoutedNoQueue_should_filter_vl_high_resolution_images_for_non_dashscope() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider provider = new ResolvedProvider(
                "p-openai",
                "OPENAI_COMPAT",
                "http://example.invalid",
                "k",
                "gpt-any",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p-openai")).thenReturn(provider);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1));

        String rawJson = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.trim();

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> when(mock.chatCompletionsOnce(any())).thenReturn(rawJson)
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            gateway.chatOnceRoutedNoQueue(
                    LlmQueueTaskType.TEXT_CHAT,
                    "p-openai",
                    null,
                    List.of(ChatMessage.user("hi")),
                    0.2,
                    null,
                    null,
                    null,
                    Boolean.TRUE,
                    128,
                    Map.of("vl_high_resolution_images", true, "foo", "bar")
            );

            OpenAiCompatClient client = mocked.constructed().get(0);
            ArgumentCaptor<OpenAiCompatClient.ChatRequest> reqCap = ArgumentCaptor.forClass(OpenAiCompatClient.ChatRequest.class);
            verify(client).chatCompletionsOnce(reqCap.capture());
            OpenAiCompatClient.ChatRequest req = reqCap.getValue();
            assertEquals(null, req.enableThinking());
            assertEquals(true, req.extraBody().containsKey("foo"));
            assertEquals(false, req.extraBody().containsKey("vl_high_resolution_images"));
        }
    }

    @Test
    void chatStreamRouted_should_wrap_checked_exception_when_model_override_path_fails() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider provider = provider("p1", "OPENAI_COMPAT", "m-default");
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider);
        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("t1");
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> sup =
                    (LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>) inv.getArgument(4);
            return sup.get(task);
        });

        OpenAiCompatClient.SseLineConsumer consumer = mock(OpenAiCompatClient.SseLineConsumer.class);

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> Mockito.doThrow(new IOException("HTTP 400")).when(mock).chatCompletionsStream(any(), any())
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.chatStreamRouted(
                            LlmQueueTaskType.TEXT_CHAT,
                            "p1",
                            "m-override",
                            List.of(ChatMessage.user("hi")),
                            0.2,
                            null,
                            null,
                            null,
                            consumer
                    )
            );
            assertTrue(ex.getMessage().contains("上游AI流式调用失败"));
            verify(mocked.constructed().get(0), atLeastOnce()).chatCompletionsStream(any(), any());
        }
    }

    @Test
    void chatStreamRouted_should_throw_when_no_route_target_and_fallback_fails() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(null);
        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("p-fallback"));
        when(aiProvidersConfigService.resolveProvider("p-fallback"))
                .thenReturn(provider("p-fallback", "OPENAI_COMPAT", "m-fallback"));

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("t1");
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> sup =
                    (LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>) inv.getArgument(4);
            return sup.get(task);
        });

        OpenAiCompatClient.SseLineConsumer consumer = mock(OpenAiCompatClient.SseLineConsumer.class);
        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> Mockito.doThrow(new IllegalArgumentException("bad request")).when(mock).chatCompletionsStream(any(), any())
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.chatStreamRouted(
                            LlmQueueTaskType.TEXT_CHAT,
                            null,
                            null,
                            List.of(ChatMessage.user("hi")),
                            0.2,
                            null,
                            null,
                            null,
                            consumer
                    )
            );
            assertTrue(ex.getMessage().contains("上游AI流式调用失败"));

            ArgumentCaptor<LlmRoutingTelemetryService.RoutingDecisionEvent> events =
                    ArgumentCaptor.forClass(LlmRoutingTelemetryService.RoutingDecisionEvent.class);
            verify(llmRoutingTelemetryService, atLeastOnce()).record(events.capture());
            assertTrue(events.getAllValues().stream().anyMatch(e -> "ROUTE_NO_TARGET".equals(e.kind())));
            assertTrue(events.getAllValues().stream().anyMatch(e -> "FALLBACK_ATTEMPT".equals(e.kind())));
        }
    }

    @Test
    void chatStreamRouted_should_wrap_checked_exception_on_provider_direct_path() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider p1 = provider("p1", "OPENAI_COMPAT", "m-default");
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p1);
        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("t1");
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> sup =
                    (LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>) inv.getArgument(4);
            return sup.get(task);
        });

        OpenAiCompatClient.SseLineConsumer consumer = mock(OpenAiCompatClient.SseLineConsumer.class);
        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> Mockito.doThrow(new IOException("HTTP 400")).when(mock).chatCompletionsStream(any(), any())
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.chatStreamRouted(
                            LlmQueueTaskType.TEXT_CHAT,
                            "p1",
                            null,
                            List.of(ChatMessage.user("hi")),
                            0.2,
                            null,
                            null,
                            null,
                            consumer
                    )
            );
            assertTrue(ex.getMessage().contains("上游AI流式调用失败"));
            verify(mocked.constructed().get(0), atLeastOnce()).chatCompletionsStream(any(), any());
        }
    }

    @Test
    void chatStreamRouted_should_fail_without_recordFailure_when_non_retriable_before_stream_started() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        LlmRoutingService.RouteTarget t1 = new LlmRoutingService.RouteTarget(
                new LlmRoutingService.TargetId("p1", "m1"),
                1,
                1,
                null
        );
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(t1);
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider("p1", "OPENAI_COMPAT", "m-default"));

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("t1");
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> sup =
                    (LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>) inv.getArgument(4);
            return sup.get(task);
        });

        OpenAiCompatClient.SseLineConsumer consumer = mock(OpenAiCompatClient.SseLineConsumer.class);
        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> Mockito.doThrow(new IllegalArgumentException("bad request")).when(mock).chatCompletionsStream(any(), any())
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.chatStreamRouted(
                            LlmQueueTaskType.TEXT_CHAT,
                            null,
                            null,
                            List.of(ChatMessage.user("hi")),
                            0.2,
                            null,
                            null,
                            null,
                            consumer
                    )
            );
            assertTrue(ex.getMessage().contains("上游AI流式调用失败"));
            verify(llmRoutingService, never()).recordFailure(eq(LlmQueueTaskType.TEXT_CHAT), eq(t1), any());
            verify(mocked.constructed().get(0), atLeastOnce()).chatCompletionsStream(any(), any());
        }
    }

    @Test
    void chatStreamRouted_should_continue_to_fallback_when_route_call_throws_retriable_checked_exception() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        LlmRoutingService.RouteTarget t1 = new LlmRoutingService.RouteTarget(
                new LlmRoutingService.TargetId("p1", "m1"),
                1,
                1,
                null
        );
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(t1);

        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider("p1", "OPENAI_COMPAT", "m1"));
        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("pf"));
        when(aiProvidersConfigService.resolveProvider("pf")).thenReturn(provider("pf", "OPENAI_COMPAT", "mf"));

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("t-fallback");
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            String providerId = inv.getArgument(1);
            if ("p1".equals(providerId)) {
                throw new IOException("HTTP 429");
            }
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> sup =
                    (LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>) inv.getArgument(4);
            return sup.get(task);
        });

        OpenAiCompatClient.SseLineConsumer consumer = mock(OpenAiCompatClient.SseLineConsumer.class);
        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> Mockito.doAnswer(inv -> {
                    OpenAiCompatClient.SseLineConsumer c = inv.getArgument(1);
                    c.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}");
                    c.onLine("data: [DONE]");
                    return null;
                }).when(mock).chatCompletionsStream(any(), any())
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            LlmGateway.RoutedChatStreamResult res = gateway.chatStreamRouted(
                    LlmQueueTaskType.TEXT_CHAT,
                    null,
                    null,
                    List.of(ChatMessage.user("hi")),
                    0.2,
                    null,
                    null,
                    null,
                    consumer
            );

            assertEquals("pf", res.providerId());
            assertEquals("mf", res.model());
            verify(llmRoutingService, times(1)).recordFailure(eq(LlmQueueTaskType.TEXT_CHAT), eq(t1), any());
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void chatStreamRouted_should_cover_override_success_and_runtime_rethrow_branches() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider p1 = provider("p1", "OPENAI_COMPAT", "m-default");
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p1);

        AtomicInteger seq = new AtomicInteger(0);
        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("t-stream");
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            int n = seq.incrementAndGet();
            if (n == 1) {
                @SuppressWarnings("unchecked")
                LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> sup =
                        (LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>) inv.getArgument(4);
                return sup.get(task);
            }
            throw new IllegalArgumentException("stream-runtime-" + n);
        });

        OpenAiCompatClient.SseLineConsumer consumer = mock(OpenAiCompatClient.SseLineConsumer.class);
        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> Mockito.doAnswer(inv -> {
                    OpenAiCompatClient.SseLineConsumer c = inv.getArgument(1);
                    c.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}");
                    c.onLine("data: [DONE]");
                    return null;
                }).when(mock).chatCompletionsStream(any(), any())
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            LlmGateway.RoutedChatStreamResult ok = gateway.chatStreamRouted(
                    LlmQueueTaskType.TEXT_CHAT,
                    "p1",
                    "m-override",
                    List.of(ChatMessage.user("hi")),
                    0.2,
                    null,
                    null,
                    null,
                    consumer
            );
            assertEquals("p1", ok.providerId());
            assertEquals("m-override", ok.model());

            IllegalArgumentException ex1 = assertThrows(
                    IllegalArgumentException.class,
                    () -> gateway.chatStreamRouted(
                            LlmQueueTaskType.TEXT_CHAT,
                            "p1",
                            "m-override",
                            List.of(ChatMessage.user("hi")),
                            0.2,
                            null,
                            null,
                            null,
                            consumer
                    )
            );
            assertTrue(ex1.getMessage().contains("stream-runtime-2"));

            IllegalArgumentException ex2 = assertThrows(
                    IllegalArgumentException.class,
                    () -> gateway.chatStreamRouted(
                            LlmQueueTaskType.TEXT_CHAT,
                            "p1",
                            null,
                            List.of(ChatMessage.user("hi")),
                            0.2,
                            null,
                            null,
                            null,
                            consumer
                    )
            );
            assertTrue(ex2.getMessage().contains("stream-runtime-3"));
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void chatStreamRouted_should_throw_on_non_retriable_route_checked_exception() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        LlmRoutingService.RouteTarget t1 = new LlmRoutingService.RouteTarget(
                new LlmRoutingService.TargetId("p1", "m1"),
                1,
                1,
                null
        );
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(t1);
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider("p1", "OPENAI_COMPAT", "m1"));
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenThrow(new IOException("HTTP 400 bad request"));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> gateway.chatStreamRouted(
                        LlmQueueTaskType.TEXT_CHAT,
                        null,
                        null,
                        List.of(ChatMessage.user("hi")),
                        0.2,
                        null,
                        null,
                        null,
                        line -> {
                        }
                )
        );
        assertTrue(ex.getMessage().contains("上游AI流式调用失败"));
    }

    @Test
    void chatStreamRouted_should_prefer_last_route_error_when_fallback_checked_exception_occurs() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        LlmRoutingService.RouteTarget t1 = new LlmRoutingService.RouteTarget(
                new LlmRoutingService.TargetId("p1", "m1"),
                1,
                1,
                null
        );
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(t1);
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider("p1", "OPENAI_COMPAT", "m1"));
        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("pf"));
        when(aiProvidersConfigService.resolveProvider("pf")).thenReturn(provider("pf", "OPENAI_COMPAT", "mf"));

        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            String providerId = inv.getArgument(1);
            if ("p1".equals(providerId)) {
                throw new IOException("HTTP 429 route");
            }
            throw new IOException("fallback checked");
        });

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> gateway.chatStreamRouted(
                        LlmQueueTaskType.TEXT_CHAT,
                        null,
                        null,
                        List.of(ChatMessage.user("hi")),
                        0.2,
                        null,
                        null,
                        null,
                        line -> {
                        }
                )
        );
        assertTrue(ex.getMessage().contains("HTTP 429 route"));
    }

    @Test
    void chatStreamRouted_should_rethrow_runtime_exception_from_fallback() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(null);
        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("pf"));
        when(aiProvidersConfigService.resolveProvider("pf")).thenReturn(provider("pf", "OPENAI_COMPAT", "mf"));
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenThrow(new RuntimeException("fallback runtime"));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> gateway.chatStreamRouted(
                        LlmQueueTaskType.TEXT_CHAT,
                        null,
                        null,
                        List.of(ChatMessage.user("hi")),
                        0.2,
                        null,
                        null,
                        null,
                        line -> {
                        }
                )
        );
        assertTrue(ex.getMessage().contains("fallback runtime"));
    }

    @Test
    void chatStreamRouted_should_cover_blank_model_override_and_blank_provider_id_paths() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(null);
        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("pf"));
        when(aiProvidersConfigService.resolveProvider("pf")).thenReturn(provider("pf", "OPENAI_COMPAT", "mf"));
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenThrow(new RuntimeException("fallback-runtime-blank"));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> gateway.chatStreamRouted(
                        LlmQueueTaskType.TEXT_CHAT,
                        "   ",
                        "   ",
                        List.of(ChatMessage.user("hi")),
                        0.2,
                        null,
                        null,
                        null,
                        line -> {
                        }
                )
        );
        assertTrue(ex.getMessage().contains("fallback-runtime-blank"));
    }

    @Test
    void chatStreamRouted_should_cover_stream_call_failed_exception_with_null_cause_path() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        LlmRoutingService.RouteTarget t1 = new LlmRoutingService.RouteTarget(
                new LlmRoutingService.TargetId("p1", "m1"),
                1,
                1,
                null
        );
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(t1);
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider("p1", "OPENAI_COMPAT", "m1"));
        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("pf"));
        when(aiProvidersConfigService.resolveProvider("pf")).thenReturn(provider("pf", "OPENAI_COMPAT", "mf"));

        Class<?> streamExClass = Class.forName("com.example.EnterpriseRagCommunity.service.ai.LlmGateway$StreamCallFailedException");
        Constructor<?> ctor = streamExClass.getDeclaredConstructor(String.class, Throwable.class, boolean.class);
        ctor.setAccessible(true);
        Exception crafted = (Exception) ctor.newInstance("HTTP 429 crafted", null, false);

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("t-fallback-ok");
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            String providerId = inv.getArgument(1);
            if ("p1".equals(providerId)) {
                throw crafted;
            }
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> sup =
                    (LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>) inv.getArgument(4);
            return sup.get(task);
        });

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> Mockito.doAnswer(inv -> {
                    OpenAiCompatClient.SseLineConsumer c = inv.getArgument(1);
                    c.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}");
                    c.onLine("data: [DONE]");
                    return null;
                }).when(mock).chatCompletionsStream(any(), any())
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.chatStreamRouted(
                            LlmQueueTaskType.TEXT_CHAT,
                            null,
                            null,
                            List.of(ChatMessage.user("hi")),
                            0.2,
                            null,
                            null,
                            null,
                            line -> {
                            }
                    )
            );
            assertTrue(ex.getMessage().contains("上游AI流式调用失败"));
        }
    }

    @Test
    void embedOnceRouted_should_use_embedding_service_when_model_override_provided() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        AiEmbeddingService.EmbeddingResult emb = dummyEmbedding("m-ovr");
        when(aiEmbeddingService.embedOnceForTask(eq("x"), eq("m-ovr"), eq("p1"), eq(LlmQueueTaskType.EMBEDDING))).thenReturn(emb);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        AiEmbeddingService.EmbeddingResult res = gateway.embedOnceRouted(LlmQueueTaskType.EMBEDDING, "p1", "m-ovr", "x");
        assertEquals("m-ovr", res.model());
        verify(llmRoutingService, never()).getPolicy(anyString());
        verify(llmRoutingService, never()).getPolicy(any(LlmQueueTaskType.class));
        verify(llmRoutingTelemetryService, never()).record(any());
    }

    @Test
    void embedOnceRouted_should_throw_no_target_message_when_no_provider_and_no_target() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.EMBEDDING))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.EMBEDDING), any())).thenReturn(null);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        Exception ex = assertThrows(
                Exception.class,
                () -> gateway.embedOnceRouted(LlmQueueTaskType.EMBEDDING, null, null, "x")
        );
        assertTrue(ex.getMessage().contains("no eligible upstream target"));
        assertTrue(!ex.getMessage().contains("providerId="));
        verify(llmRoutingTelemetryService, atLeastOnce()).record(any());
    }

    @Test
    void embedOnceRouted_should_retry_then_succeed() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.EMBEDDING))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 2, 1, 500));
        LlmRoutingService.RouteTarget t1 = new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p1", "m1"), 1, 1, null);
        LlmRoutingService.RouteTarget t2 = new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p2", "m2"), 1, 1, null);
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.EMBEDDING), any())).thenReturn(t1, t2);

        when(aiEmbeddingService.embedOnceForTask(eq("x"), eq("m1"), eq("p1"), eq(LlmQueueTaskType.EMBEDDING)))
                .thenThrow(new SocketTimeoutException("timeout"));
        when(aiEmbeddingService.embedOnceForTask(eq("x"), eq("m2"), eq("p2"), eq(LlmQueueTaskType.EMBEDDING)))
                .thenReturn(dummyEmbedding("m2"));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        AiEmbeddingService.EmbeddingResult res = gateway.embedOnceRouted(LlmQueueTaskType.EMBEDDING, null, null, "x");
        assertEquals("m2", res.model());
        verify(llmRoutingService, times(1)).recordFailure(eq(LlmQueueTaskType.EMBEDDING), eq(t1), any());
        verify(llmRoutingService, times(1)).recordSuccess(eq(LlmQueueTaskType.EMBEDDING), eq(t2));
        verify(llmRoutingTelemetryService, atLeastOnce()).record(any());
    }

    @Test
    void rerankOnceRouted_should_use_service_when_model_override_provided() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        AiRerankService.RerankResult rr = dummyRerank("p1", "m-ovr");
        when(aiRerankService.rerankOnce(eq("p1"), eq("m-ovr"), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(rr);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        AiRerankService.RerankResult res = gateway.rerankOnceRouted(LlmQueueTaskType.RERANK, "p1", "m-ovr", "q", List.of("d1"), 1, null, null, null);
        assertEquals("m-ovr", res.model());
        verify(llmRoutingService, never()).getPolicy(anyString());
        verify(llmRoutingService, never()).getPolicy(any(LlmQueueTaskType.class));
    }

    @Test
    void rerankOnceRouted_should_use_service_when_provider_id_only() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        AiRerankService.RerankResult rr = dummyRerank("p1", "m-any");
        when(aiRerankService.rerankOnce(eq("p1"), eq(null), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(rr);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        AiRerankService.RerankResult res = gateway.rerankOnceRouted(LlmQueueTaskType.RERANK, "p1", null, "q", List.of("d1"), 1, null, null, null);
        assertEquals("p1", res.providerId());
        verify(llmRoutingService, never()).getPolicy(anyString());
        verify(llmRoutingService, never()).getPolicy(any(LlmQueueTaskType.class));
    }

    @Test
    void rerankOnceRouted_should_retry_then_succeed() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.RERANK))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 2, 1, 500));
        LlmRoutingService.RouteTarget t1 = new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p1", "m1"), 1, 1, null);
        LlmRoutingService.RouteTarget t2 = new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p2", "m2"), 1, 1, null);
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.RERANK), any())).thenReturn(t1, t2);

        when(aiRerankService.rerankOnce(eq("p1"), eq("m1"), anyString(), any(), any(), any(), any(), any()))
                .thenThrow(new IOException("HTTP 429"));
        when(aiRerankService.rerankOnce(eq("p2"), eq("m2"), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(dummyRerank("p2", "m2"));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        AiRerankService.RerankResult res = gateway.rerankOnceRouted(LlmQueueTaskType.RERANK, null, null, "q", List.of("d1"), 1, null, null, null);
        assertEquals("p2", res.providerId());
        verify(llmRoutingService, times(1)).recordFailure(eq(LlmQueueTaskType.RERANK), eq(t1), any());
        verify(llmRoutingService, times(1)).recordSuccess(eq(LlmQueueTaskType.RERANK), eq(t2));
        verify(llmRoutingTelemetryService, atLeastOnce()).record(any());
    }

    @Test
    void rerankOnceRouted_should_throw_no_target_message_when_no_target() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.RERANK))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.RERANK), any())).thenReturn(null);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        Exception ex = assertThrows(
                Exception.class,
                () -> gateway.rerankOnceRouted(LlmQueueTaskType.RERANK, null, null, "q", List.of("d1"), 1, null, null, null)
        );
        assertTrue(ex.getMessage().contains("no eligible upstream target"));
        verify(llmRoutingTelemetryService, atLeastOnce()).record(any());
    }

    @Test
    void resolve_should_accept_null_type_as_openai_compat() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider p = new ResolvedProvider(
                "p1",
                null,
                "http://example.invalid",
                "k",
                "m-default",
                "e-default",
                Map.of(),
                Map.of(),
                1000,
                1000
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        ResolvedProvider res = gateway.resolve("p1");
        assertEquals("p1", res.id());
    }

    @Test
    void resolve_should_throw_when_no_provider_configured() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(null);
        when(aiProvidersConfigService.resolveActiveProvider()).thenReturn(null);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> gateway.resolve("p1"));
        assertTrue(ex.getMessage().contains("未配置任何有效的 AI 模型提供商"));
    }

    @Test
    void chatOnceRouted_should_use_metrics_extractor_and_tokenizer_decision_when_usage_missing() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider provider = provider("p1", "OPENAI_COMPAT", "m-default");
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString())).thenReturn(null);
        when(tokenCountService.decideChatTokens(
                anyString(),
                anyString(),
                anyBoolean(),
                any(),
                any(),
                any(),
                eq(true)
        )).thenReturn(new TokenCountService.TokenDecision(11, 22, 33, null, "mock"));

        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<String> sup = (LlmCallQueueService.CheckedTaskSupplier<String>) inv.getArgument(4);
            @SuppressWarnings("unchecked")
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = (LlmCallQueueService.ResultMetricsExtractor<String>) inv.getArgument(5);
            LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
            when(task.id()).thenReturn("t1");
            String raw = sup.get(task);
            extractor.extract(raw);
            return raw;
        });

        String rawJson = """
                {"choices":[{"message":{"content":"ok"}}]}
                """.trim();

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> when(mock.chatCompletionsOnce(any())).thenReturn(rawJson)
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            LlmGateway.RoutedChatOnceResult res = gateway.chatOnceRouted(
                    LlmQueueTaskType.MODERATION_CHUNK,
                    "p1",
                    null,
                    List.of(ChatMessage.user("hi")),
                    0.2,
                    null,
                    null,
                    null,
                    Boolean.FALSE
            );

            assertEquals(33, res.usage().totalTokens());
            assertEquals(11, res.usage().promptTokens());
            assertEquals(22, res.usage().completionTokens());
            verify(tokenCountService, atLeastOnce()).decideChatTokens(anyString(), anyString(), anyBoolean(), any(), any(), any(), eq(true));
            verify(mocked.constructed().get(0), atLeastOnce()).chatCompletionsOnce(any());
        }
    }

    @Test
    void chatOnceRoutedNoQueue_should_use_tokenizer_decision_when_usage_missing() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider provider = provider("p1", "OPENAI_COMPAT", "m-default");
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString())).thenReturn(null);
        when(tokenCountService.decideChatTokens(
                anyString(),
                anyString(),
                anyBoolean(),
                any(),
                any(),
                any(),
                eq(true)
        )).thenReturn(new TokenCountService.TokenDecision(7, 8, 15, null, "mock"));

        String rawJson = """
                {"choices":[{"message":{"content":"ok"}}]}
                """.trim();

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> when(mock.chatCompletionsOnce(any())).thenReturn(rawJson)
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            LlmGateway.RoutedChatOnceResult res = gateway.chatOnceRoutedNoQueue(
                    LlmQueueTaskType.MODERATION_CHUNK,
                    "p1",
                    null,
                    List.of(ChatMessage.user("hi")),
                    0.2,
                    null,
                    null,
                    null,
                    Boolean.FALSE,
                    64,
                    Map.of("foo", "bar")
            );

            assertEquals(15, res.usage().totalTokens());
            assertEquals(7, res.usage().promptTokens());
            assertEquals(8, res.usage().completionTokens());
            verify(tokenCountService, atLeastOnce()).decideChatTokens(anyString(), anyString(), anyBoolean(), any(), any(), any(), eq(true));
            verify(mocked.constructed().get(0), atLeastOnce()).chatCompletionsOnce(any());
        }
    }

    @Test
    void chatOnceRouted_should_keep_usage_when_tokenizer_returns_null() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider provider = provider("p1", "OPENAI_COMPAT", "m-default");
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(provider);
        LlmCallQueueService.UsageMetrics usage = new LlmCallQueueService.UsageMetrics(3, 4, 7, 4);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString())).thenReturn(usage);
        when(tokenCountService.decideChatTokens(
                anyString(),
                anyString(),
                anyBoolean(),
                any(),
                any(),
                any(),
                eq(true)
        )).thenReturn(null);

        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<String> sup = (LlmCallQueueService.CheckedTaskSupplier<String>) inv.getArgument(4);
            @SuppressWarnings("unchecked")
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = (LlmCallQueueService.ResultMetricsExtractor<String>) inv.getArgument(5);
            LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
            when(task.id()).thenReturn("t1");
            String raw = sup.get(task);
            extractor.extract(raw);
            return raw;
        });

        String rawJson = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}
                """.trim();
        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> when(mock.chatCompletionsOnce(any())).thenReturn(rawJson)
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );
            LlmGateway.RoutedChatOnceResult res = gateway.chatOnceRouted(
                    LlmQueueTaskType.MODERATION_CHUNK,
                    "p1",
                    null,
                    List.of(ChatMessage.user("hi")),
                    0.2,
                    null,
                    null,
                    null,
                    Boolean.TRUE
            );
            assertEquals(7, res.usage().totalTokens());
            assertEquals(3, res.usage().promptTokens());
            assertEquals(4, res.usage().completionTokens());
            verify(tokenCountService, atLeastOnce()).decideChatTokens(anyString(), anyString(), anyBoolean(), any(), any(), any(), eq(true));
            verify(mocked.constructed().get(0), atLeastOnce()).chatCompletionsOnce(any());
        }
    }

    @Test
    void chatOnceRoutedNoQueue_should_throw_immediately_when_routed_call_non_retriable_fails() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider p1 = provider("p1", "OPENAI_COMPAT", "m1");
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p1);
        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 2, 1, 500));
        LlmRoutingService.TargetId t1 = new LlmRoutingService.TargetId("p1", "m1");
        LlmRoutingService.RouteTarget r1 = new LlmRoutingService.RouteTarget(t1, 1, 1, null);
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(r1);

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> Mockito.doThrow(new IllegalArgumentException("bad req"))
                        .when(mock).chatCompletionsOnce(any())
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> gateway.chatOnceRoutedNoQueue(
                    LlmQueueTaskType.TEXT_CHAT,
                    null,
                    null,
                    List.of(ChatMessage.user("hi")),
                    0.2,
                    null,
                    null,
                    null,
                    Boolean.TRUE,
                    null,
                    Map.of()
            ));
            assertTrue(ex.getMessage().contains("上游AI调用失败"));
            verify(llmRoutingService, never()).recordSuccess(any(), any());
            verify(mocked.constructed().get(0), atLeastOnce()).chatCompletionsOnce(any());
        }
    }

    @Test
    void chatOnceRoutedNoQueue_should_fallback_after_retriable_route_failure() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider p1 = provider("p1", "OPENAI_COMPAT", "m1");
        ResolvedProvider pf = provider("pf", "OPENAI_COMPAT", "mf");
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p1);
        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("pf"));
        when(aiProvidersConfigService.resolveProvider("pf")).thenReturn(pf);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 2, 1, 500));
        LlmRoutingService.TargetId t1 = new LlmRoutingService.TargetId("p1", "m1");
        LlmRoutingService.RouteTarget r1 = new LlmRoutingService.RouteTarget(t1, 1, 1, null);
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(r1, null);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1));

        String okRaw = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.trim();
        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, ctx) -> {
                    if (ctx.getCount() == 1) {
                        Mockito.doThrow(new IOException("HTTP 429")).when(mock).chatCompletionsOnce(any());
                    } else {
                        when(mock.chatCompletionsOnce(any())).thenReturn(okRaw);
                    }
                }
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );
            LlmGateway.RoutedChatOnceResult res = gateway.chatOnceRoutedNoQueue(
                    LlmQueueTaskType.TEXT_CHAT,
                    null,
                    null,
                    List.of(ChatMessage.user("hi")),
                    0.2,
                    null,
                    null,
                    null,
                    Boolean.TRUE,
                    null,
                    Map.of()
            );
            assertEquals("pf", res.providerId());
            assertEquals("mf", res.model());
            verify(llmRoutingService, atLeastOnce()).recordFailure(any(), any(), anyString());
            assertTrue(mocked.constructed().size() >= 2);
        }
    }

    @Test
    void chatOnceRouted_should_use_current_exception_when_fallback_fails_without_previous_route_error() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(null);
        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("pf"));
        when(aiProvidersConfigService.resolveProvider("pf")).thenReturn(provider("pf", "OPENAI_COMPAT", "mf"));
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenThrow(new IOException("fallback io"));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> gateway.chatOnceRouted(LlmQueueTaskType.TEXT_CHAT, null, null, List.of(ChatMessage.user("hi")), 0.2)
        );
        assertTrue(ex.getMessage().contains("fallback io"));
    }

    @Test
    void chatOnceRoutedNoQueue_should_use_current_exception_when_fallback_fails_without_previous_route_error() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.TEXT_CHAT))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.TEXT_CHAT), any())).thenReturn(null);
        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("pf"));
        when(aiProvidersConfigService.resolveProvider("pf")).thenReturn(provider("pf", "OPENAI_COMPAT", "mf"));

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> when(mock.chatCompletionsOnce(any())).thenThrow(new IOException("fallback noqueue io"))
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.chatOnceRoutedNoQueue(
                            LlmQueueTaskType.TEXT_CHAT,
                            null,
                            null,
                            List.of(ChatMessage.user("hi")),
                            0.2,
                            null,
                            null,
                            null,
                            null,
                            null,
                            Map.of()
                    )
            );
            assertTrue(ex.getMessage().contains("fallback noqueue io"));
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void embedOnceRouted_should_rethrow_non_retriable_ioexception_directly() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        when(llmRoutingService.getPolicy(LlmQueueTaskType.EMBEDDING))
                .thenReturn(new LlmRoutingService.Policy(LlmRoutingService.Strategy.WEIGHTED_RR, 1, 1, 500));
        LlmRoutingService.RouteTarget t1 = new LlmRoutingService.RouteTarget(new LlmRoutingService.TargetId("p1", "m1"), 1, 1, null);
        when(llmRoutingService.pickNext(eq(LlmQueueTaskType.EMBEDDING), any())).thenReturn(t1);
        when(aiEmbeddingService.embedOnceForTask(eq("x"), eq("m1"), eq("p1"), eq(LlmQueueTaskType.EMBEDDING)))
                .thenThrow(new IOException("HTTP 400"));

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        IOException ex = assertThrows(
                IOException.class,
                () -> gateway.embedOnceRouted(LlmQueueTaskType.EMBEDDING, null, null, "x")
        );
        assertTrue(ex.getMessage().contains("HTTP 400"));
    }

    @Test
    void chatOnceRoutedNoQueue_should_use_provider_direct_path_success() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider p1 = new ResolvedProvider(
                "p1", "OPENAI_COMPAT", "http://example.invalid", "k", "m-default", "e-default", Map.of(), Map.of(), 1000, 1000
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p1);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 3, 2));

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> when(mock.chatCompletionsOnce(any()))
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}")
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            LlmGateway.RoutedChatOnceResult res = gateway.chatOnceRoutedNoQueue(
                    LlmQueueTaskType.TEXT_CHAT,
                    "p1",
                    null,
                    List.of(ChatMessage.user("hi")),
                    0.2,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of()
            );
            assertEquals("p1", res.providerId());
            assertEquals("m-default", res.model());
            assertNotNull(res.usage());
            verify(llmRoutingService, never()).pickNext(any(), any());
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void chatOnceRoutedNoQueue_should_wrap_checked_exception_on_model_override_path() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider p1 = new ResolvedProvider(
                "p1", "OPENAI_COMPAT", "http://example.invalid", "k", "m-default", "e-default", Map.of(), Map.of(), 1000, 1000
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p1);

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> when(mock.chatCompletionsOnce(any())).thenThrow(new IOException("io model-override"))
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.chatOnceRoutedNoQueue(
                            LlmQueueTaskType.TEXT_CHAT,
                            "p1",
                            "m-override",
                            List.of(ChatMessage.user("hi")),
                            0.2,
                            null,
                            null,
                            null,
                            null,
                            null,
                            Map.of()
                    )
            );
            assertTrue(ex.getMessage().contains("上游AI调用失败"));
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void chatOnceRoutedNoQueue_should_wrap_checked_exception_on_provider_direct_path() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider p1 = new ResolvedProvider(
                "p1", "OPENAI_COMPAT", "http://example.invalid", "k", "m-default", "e-default", Map.of(), Map.of(), 1000, 1000
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p1);

        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> when(mock.chatCompletionsOnce(any())).thenThrow(new IOException("io provider-direct"))
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.chatOnceRoutedNoQueue(
                            LlmQueueTaskType.TEXT_CHAT,
                            "p1",
                            null,
                            List.of(ChatMessage.user("hi")),
                            0.2,
                            null,
                            null,
                            null,
                            null,
                            null,
                            Map.of()
                    )
            );
            assertTrue(ex.getMessage().contains("上游AI调用失败"));
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void null_taskType_paths_should_fallback_to_default_task_types() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        ResolvedProvider p1 = new ResolvedProvider(
                "p1", "OPENAI_COMPAT", "http://example.invalid", "k", "m-default", "e-default", Map.of(), Map.of(), 1000, 1000
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p1);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 3, 2));

        when(aiEmbeddingService.embedOnceForTask(eq("x"), eq("m-ovr"), eq("p1"), eq(LlmQueueTaskType.EMBEDDING)))
                .thenReturn(dummyEmbedding("m-ovr"));
        when(aiRerankService.rerankOnce(eq("p1"), eq("m-ovr"), eq("q"), eq(List.of("d1")), eq(1), eq(null), eq(null), eq(null)))
                .thenReturn(dummyRerank("p1", "m-ovr"));

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("t-null");
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> sup =
                    (LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>) inv.getArgument(4);
            return sup.get(task);
        });

        OpenAiCompatClient.SseLineConsumer consumer = mock(OpenAiCompatClient.SseLineConsumer.class);
        try (MockedConstruction<OpenAiCompatClient> mocked = Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (mock, _ctx) -> {
                    when(mock.chatCompletionsOnce(any()))
                            .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}")
                            .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok2\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}");
                    Mockito.doAnswer(inv -> {
                        OpenAiCompatClient.SseLineConsumer c = inv.getArgument(1);
                        c.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}");
                        c.onLine("data: [DONE]");
                        return null;
                    }).when(mock).chatCompletionsStream(any(), any());
                }
        )) {
            LlmGateway gateway = new LlmGateway(
                    aiProvidersConfigService,
                    aiEmbeddingService,
                    aiRerankService,
                    llmCallQueueService,
                    llmRoutingService,
                    llmRoutingTelemetryService,
                    tokenCountService
            );

            LlmGateway.RoutedChatOnceResult c1 = gateway.chatOnceRouted(
                    null, "p1", "m-ovr", List.of(ChatMessage.user("hi")), 0.2
            );
            assertEquals("p1", c1.providerId());

            LlmGateway.RoutedChatOnceResult c2 = gateway.chatOnceRoutedNoQueue(
                    null, "p1", "m-ovr", List.of(ChatMessage.user("hi")), 0.2, null, null, null, null, null, Map.of()
            );
            assertEquals("p1", c2.providerId());

            LlmGateway.RoutedChatStreamResult s = gateway.chatStreamRouted(
                    null, "p1", null, List.of(ChatMessage.user("hi")), 0.2, null, null, null, consumer
            );
            assertEquals("p1", s.providerId());

            assertNotNull(gateway.embedOnceRouted(null, "p1", "m-ovr", "x"));
            assertNotNull(gateway.rerankOnceRouted(null, "p1", "m-ovr", "q", List.of("d1"), 1, null, null, null));
            assertTrue(mocked.constructed().size() >= 1);
        }
    }
}
