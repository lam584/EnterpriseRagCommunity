package com.example.EnterpriseRagCommunity.service.ai;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.EnterpriseRagCommunity.service.ai.AiProvidersConfigService.ResolvedProvider;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;


class LlmGatewayPart3Test {

    private static ResolvedProvider provider(String id, String defaultChatModel) {
        return new ResolvedProvider(
                id,
                "OPENAI_COMPAT",
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
        when(aiProvidersConfigService.resolveProvider("p-fallback")).thenReturn(provider("p-fallback", "m-fallback"));

        String okRawJson = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.trim();
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                org.mockito.ArgumentMatchers.<LlmCallQueueService.CheckedTaskSupplier<String>>any(),
                any()
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
        assertTrue(ex.getMessage().contains("璺敱鐩爣"));
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
        when(aiProvidersConfigService.resolveActiveProvider()).thenReturn(provider("p-active", "m-active"));

        String okRawJson = """
                {"choices":[{"message":{"content":"ok"}}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.trim();
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                anyString(),
                anyString(),
                anyInt(),
                org.mockito.ArgumentMatchers.<LlmCallQueueService.CheckedTaskSupplier<String>>any(),
                any()
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

            OpenAiCompatClient client = mocked.constructed().getFirst();
            ArgumentCaptor<OpenAiCompatClient.ChatRequest> reqCap = ArgumentCaptor.forClass(OpenAiCompatClient.ChatRequest.class);
            verify(client).chatCompletionsOnce(reqCap.capture());
            OpenAiCompatClient.ChatRequest req = reqCap.getValue();
            assertEquals(Boolean.FALSE, req.enableThinking());
            assertEquals(Integer.valueOf(128), req.thinkingBudget());
            assertTrue(req.extraBody().containsKey("vl_high_resolution_images"));
            assertEquals("bar", req.extraBody().get("foo"));
        }
    }
}
