package com.example.EnterpriseRagCommunity.service.ai;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
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


class LlmGatewayPart2Test {


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
                org.mockito.ArgumentMatchers.<LlmCallQueueService.CheckedTaskSupplier<String>>any(),
                any()
        )).thenAnswer(inv -> {
            LlmCallQueueService.CheckedTaskSupplier<String> sup = inv.getArgument(4);
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

            OpenAiCompatClient client = mocked.constructed().getFirst();
            ArgumentCaptor<OpenAiCompatClient.ChatRequest> reqCap = ArgumentCaptor.forClass(OpenAiCompatClient.ChatRequest.class);
            verify(client).chatCompletionsOnce(reqCap.capture());
            List<ChatMessage> sent = reqCap.getValue().messages();
            assertTrue(String.valueOf(sent.getLast().content()).contains("/think"));
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
                org.mockito.ArgumentMatchers.<LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>>any(),
                any()
        )).thenAnswer(inv -> {
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> sup =
                    inv.getArgument(4);
            return sup.get(task);
        });

        OpenAiCompatClient.SseLineConsumer consumer = mock(OpenAiCompatClient.SseLineConsumer.class);

        try (MockedConstruction<OpenAiCompatClient> ignored = Mockito.mockConstruction(
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
                org.mockito.ArgumentMatchers.<LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>>any(),
                any()
        )).thenAnswer(inv -> {
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> sup =
                    inv.getArgument(4);
            return sup.get(task);
        });

        OpenAiCompatClient.SseLineConsumer consumer = mock(OpenAiCompatClient.SseLineConsumer.class);

        try (MockedConstruction<OpenAiCompatClient> ignored = Mockito.mockConstruction(
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
            assertTrue(ex.getMessage().contains("涓婃父AI娴佸紡璋冪敤澶辫触"));
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
}
