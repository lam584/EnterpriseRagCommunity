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
        assertTrue(ex.getMessage().contains("鏆備笉鏀寔鐨勬ā鍨嬫彁渚涘晢绫诲瀷"));
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
        assertTrue(ex.getMessage().contains("涓婃父AI璋冪敤澶辫触"));

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
}
