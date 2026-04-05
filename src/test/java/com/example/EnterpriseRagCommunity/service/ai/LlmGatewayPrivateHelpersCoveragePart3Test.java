package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class LlmGatewayPrivateHelpersCoveragePart3Test {

    private static Object callStatic(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = LlmGateway.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Object callInstance(LlmGateway gateway, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = LlmGateway.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(gateway, args);
    }

    private static Object callCallChatOnceSingleLambda(LlmGateway gateway, Class<?>[] paramTypes, Object... args) throws Exception {
        for (Method m : LlmGateway.class.getDeclaredMethods()) {
            if (!m.getName().startsWith("lambda$callChatOnceSingle$")) {
                continue;
            }
            if (!Arrays.equals(m.getParameterTypes(), paramTypes)) {
                continue;
            }
            m.setAccessible(true);
            return m.invoke(gateway, args);
        }
        throw new NoSuchMethodException("lambda$callChatOnceSingle$* with expected signature not found");
    }

    private static LlmGateway gateway() {
        return new LlmGateway(
                mock(AiProvidersConfigService.class),
                mock(AiEmbeddingService.class),
                mock(AiRerankService.class),
                mock(LlmCallQueueService.class),
                mock(LlmRoutingService.class),
                mock(LlmRoutingTelemetryService.class),
                mock(TokenCountService.class)
        );
    }

    @Test
    void callChatOnceSingleNoQueue_should_cover_strip_and_tokenizer_decision_paths() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );

        AiProvidersConfigService.ResolvedProvider provider = new AiProvidersConfigService.ResolvedProvider(
                "p1", "OPENAI_COMPAT", "https://dashscope.aliyuncs.com/compatible-mode/v1", "k", "m1", "e1", Map.of(), Map.of(), 1000, 1000
        );
        when(llmCallQueueService.parseOpenAiUsageFromJson(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(null)
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 3, 2));
        when(tokenCountService.decideChatTokens(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(new TokenCountService.TokenDecision(7, 8, 15, null, "mock"));

        try (org.mockito.MockedConstruction<OpenAiCompatClient> mocked = org.mockito.Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (m, c) -> when(m.chatCompletionsOnce(org.mockito.ArgumentMatchers.any()))
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"<think>x</think>ok\"}}]}")
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok2\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}")
        )) {
            Object r1 = callInstance(
                    gateway,
                    "callChatOnceSingleNoQueue",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, Map.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, null, null, Boolean.FALSE, null, Map.of("vl_high_resolution_images", true), 1, Map.of()
            );
            assertNotNull(r1);

            Object r2 = callInstance(
                    gateway,
                    "callChatOnceSingleNoQueue",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, Map.class
                    },
                    LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.3, null, null, null, Boolean.TRUE, null, Map.of(), 1, Map.of()
            );
            assertNotNull(r2);
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void retry_and_utility_helpers_should_cover_zero_attempts_and_null_paths() throws Exception {
        Class<?> supplierType = Class.forName("com.example.EnterpriseRagCommunity.service.ai.LlmGateway$CheckedSupplier");
        Method withRetry = LlmGateway.class.getDeclaredMethod("withRetry", int.class, supplierType);
        withRetry.setAccessible(true);
        Object okSupplier = Proxy.newProxyInstance(
                supplierType.getClassLoader(),
                new Class[]{supplierType},
                (proxy, method, args) -> "ok"
        );
        assertEquals("ok", withRetry.invoke(null, 0, okSupplier));

        Method withStreamRetry = LlmGateway.class.getDeclaredMethod(
                "withStreamRetry",
                int.class,
                OpenAiCompatClient.ChatRequest.class,
                OpenAiCompatClient.class,
                OpenAiCompatClient.SseLineConsumer.class
        );
        withStreamRetry.setAccessible(true);
        Method callStreamWithRetry = LlmGateway.class.getDeclaredMethod(
                "callStreamWithRetry",
                int.class,
                OpenAiCompatClient.ChatRequest.class,
                OpenAiCompatClient.class,
                OpenAiCompatClient.SseLineConsumer.class
        );
        callStreamWithRetry.setAccessible(true);
        OpenAiCompatClient.ChatRequest req = mock(OpenAiCompatClient.ChatRequest.class);
        OpenAiCompatClient c = mock(OpenAiCompatClient.class);
        OpenAiCompatClient.SseLineConsumer sink = line -> {
        };
        withStreamRetry.invoke(null, 0, req, c, sink);
        callStreamWithRetry.invoke(null, 0, req, c, sink);

        assertEquals(0L, callStatic("elapsedMs", new Class[]{long.class}, Long.MAX_VALUE));
        assertEquals("", callStatic("safeErrorCode", new Class[]{Throwable.class}, (Object) null));
        assertEquals("", callStatic("safeErrorMessage", new Class[]{Throwable.class}, (Object) null));
        assertEquals("", callStatic("safeErrorMessage", new Class[]{Throwable.class}, new RuntimeException((String) null)));
        String longMsg = "x".repeat(700);
        String trimmed = (String) callStatic("safeErrorMessage", new Class[]{Throwable.class}, new RuntimeException(longMsg));
        assertEquals(500, trimmed.length());

        assertEquals(Map.of("x", "1"), callStatic("mergeHeaders", new Class[]{Map.class, Map.class}, null, Map.of("x", "1")));
        assertEquals(Map.of("x", "1"), callStatic("mergeHeaders", new Class[]{Map.class, Map.class}, Map.of("x", "1"), null));

        assertNull(callStatic("filterExtraBody", new Class[]{AiProvidersConfigService.ResolvedProvider.class, Map.class}, null, Map.of()));
        assertEquals(Map.of("a", 1), callStatic("filterExtraBody", new Class[]{AiProvidersConfigService.ResolvedProvider.class, Map.class}, null, Map.of("a", 1, " ", 2)));

        assertEquals("a\r/no_think", callStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, "a\r", false, "qwen3-32b"));
        assertFalse((boolean) callStatic("shouldPreferTokenizerIn", new Class[]{LlmQueueTaskType.class}, (Object) null));
    }

    @Test
    void stream_retry_helpers_should_cover_done_and_null_line_paths() throws Exception {
        Method withStreamRetry = LlmGateway.class.getDeclaredMethod(
                "withStreamRetry",
                int.class,
                OpenAiCompatClient.ChatRequest.class,
                OpenAiCompatClient.class,
                OpenAiCompatClient.SseLineConsumer.class
        );
        withStreamRetry.setAccessible(true);
        Method callStreamWithRetry = LlmGateway.class.getDeclaredMethod(
                "callStreamWithRetry",
                int.class,
                OpenAiCompatClient.ChatRequest.class,
                OpenAiCompatClient.class,
                OpenAiCompatClient.SseLineConsumer.class
        );
        callStreamWithRetry.setAccessible(true);

        OpenAiCompatClient.ChatRequest req = mock(OpenAiCompatClient.ChatRequest.class);
        OpenAiCompatClient.SseLineConsumer sink = line -> {
        };

        OpenAiCompatClient c1 = mock(OpenAiCompatClient.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer s = inv.getArgument(1);
            s.onLine("data: [DONE]");
            throw new SocketTimeoutException("retry-1");
        }).doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer s = inv.getArgument(1);
            s.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}");
            return null;
        }).when(c1).chatCompletionsStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        withStreamRetry.invoke(null, 2, req, c1, sink);

        OpenAiCompatClient c2 = mock(OpenAiCompatClient.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer s = inv.getArgument(1);
            s.onLine("data: [DONE]");
            throw new SocketTimeoutException("rt");
        }).doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer s = inv.getArgument(1);
            s.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"ok2\"}}]}");
            return null;
        }).when(c2).chatCompletionsStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        callStreamWithRetry.invoke(null, 2, req, c2, sink);
    }

    @Test
    void normalization_and_marker_helpers_should_cover_remaining_branch_matrix() throws Exception {
        assertNull(callStatic("normalizeOpenAiCompatUsage", new Class[]{Integer.class, Integer.class, Integer.class}, null, null, null));

        LlmCallQueueService.UsageMetrics n1 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage", new Class[]{Integer.class, Integer.class, Integer.class}, 10, 2, 5
        );
        assertEquals(10, n1.promptTokens());
        assertEquals(5, n1.completionTokens());
        assertEquals(15, n1.totalTokens());

        LlmCallQueueService.UsageMetrics n2 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage", new Class[]{Integer.class, Integer.class, Integer.class}, 10, 0, 18
        );
        assertEquals(8, n2.completionTokens());
        assertEquals(18, n2.totalTokens());

        LlmCallQueueService.UsageMetrics n3 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage", new Class[]{Integer.class, Integer.class, Integer.class}, 10, 3, 20
        );
        assertEquals(13, n3.totalTokens());

        LlmCallQueueService.UsageMetrics n4 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage", new Class[]{Integer.class, Integer.class, Integer.class}, 10, null, 7
        );
        assertEquals(7, n4.completionTokens());
        assertEquals(17, n4.totalTokens());

        LlmCallQueueService.UsageMetrics n5 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage", new Class[]{Integer.class, Integer.class, Integer.class}, null, 4, 9
        );
        assertEquals(5, n5.promptTokens());

        assertTrue((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "qwen3-14b"));
        assertTrue((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "qwen-plus-2025-04-28"));
        assertTrue((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "vendor/qwen-turbo-2025-04-28"));
        assertFalse((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "qwen3-14b-thinking"));
        assertFalse((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "gpt-4o"));
        assertFalse((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, " "));

        String rt1 = (String) callStatic("removeClosedThinkBlocks", new Class[]{String.class}, "a<think>x</think> b");
        assertFalse(rt1.contains("<think>"));
        String rt2 = (String) callStatic("removeClosedThinkBlocks", new Class[]{String.class}, "a<think>x");
        assertNotNull(rt2);
        String rr1 = (String) callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, "a<reasoning>x</reasoning> b");
        assertNotNull(rr1);
        String rr2 = (String) callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, "a<reasoning>x");
        assertNotNull(rr2);

        assertNull(callStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, null, "think"));
        assertEquals("abc", callStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, "abc", "think"));
        assertEquals("x  y", callStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, "x THINK y", "think"));

        assertEquals("429", callStatic("extractErrorCode", new Class[]{Throwable.class}, new RuntimeException("HTTP 429 quota")));
        assertEquals("5xx", callStatic("extractErrorCode", new Class[]{Throwable.class}, new RuntimeException("HTTP 503 unavailable")));
        assertEquals("reset", callStatic("extractErrorCode", new Class[]{Throwable.class}, new RuntimeException("Connection reset by peer")));
        assertEquals("timeout", callStatic("extractErrorCode", new Class[]{Throwable.class}, new RuntimeException("timed out waiting")));
        assertEquals("dns", callStatic("extractErrorCode", new Class[]{Throwable.class}, new UnknownHostException("dns")));
    }

    @Test
    void lambda_callChatOnceSingle_1_should_cover_output_routing_branches() throws Exception {
        LlmGateway gateway = gateway();
        AiProvidersConfigService.ResolvedProvider provider = new AiProvidersConfigService.ResolvedProvider(
                "p1", "OPENAI_COMPAT", "http://example.invalid", "k", "m1", "e1", Map.of(), Map.of(), 1000, 1000
        );
        OpenAiCompatClient client = mock(OpenAiCompatClient.class);
        OpenAiCompatClient.ChatRequest req = mock(OpenAiCompatClient.ChatRequest.class);
        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        when(task.id()).thenReturn("task-l1");
        AtomicReference<String> idOut = new AtomicReference<>();
        List<String> outputs = new ArrayList<>();
        doAnswer(a -> {
            outputs.add(String.valueOf(a.getArguments()[0]));
            return null;
        }).when(task).reportOutput(org.mockito.ArgumentMatchers.any());

        when(client.chatCompletionsOnce(req))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"<think>x</think>ok\"}}]}")
                .thenReturn("   ");

        callCallChatOnceSingleLambda(
                gateway,
                new Class[]{
                        AtomicReference.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class, Double.class,
                        Boolean.class, Integer.class, int.class, OpenAiCompatClient.class, OpenAiCompatClient.ChatRequest.class,
                        boolean.class, LlmCallQueueService.TaskHandle.class
                },
                idOut, provider, "m1", List.of(ChatMessage.user("hi")), 0.2,
                Boolean.TRUE, null, 1, client, req, false, task
        );
        callCallChatOnceSingleLambda(
                gateway,
                new Class[]{
                        AtomicReference.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class, Double.class,
                        Boolean.class, Integer.class, int.class, OpenAiCompatClient.class, OpenAiCompatClient.ChatRequest.class,
                        boolean.class, LlmCallQueueService.TaskHandle.class
                },
                idOut, provider, "m1", List.of(ChatMessage.user("hi")), 0.2,
                Boolean.FALSE, null, 1, client, req, true, task
        );
        callCallChatOnceSingleLambda(
                gateway,
                new Class[]{
                        AtomicReference.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class, Double.class,
                        Boolean.class, Integer.class, int.class, OpenAiCompatClient.class, OpenAiCompatClient.ChatRequest.class,
                        boolean.class, LlmCallQueueService.TaskHandle.class
                },
                idOut, provider, "m1", List.of(ChatMessage.user("hi")), 0.2,
                null, null, 1, client, req, false, task
        );

        assertEquals("task-l1", idOut.get());
        assertTrue(outputs.stream().anyMatch(s -> s.contains("鏉堟挸鍤弬鍥ㄦ拱:")));
        assertTrue(outputs.stream().anyMatch(s -> s.isBlank() || s.equals("ok")));
    }

    @Test
    void callChatOnceSingleNoQueue_should_cover_usage_complete_and_incomplete_paths() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                tokenCountService
        );
        AiProvidersConfigService.ResolvedProvider provider = new AiProvidersConfigService.ResolvedProvider(
                "p1", "OPENAI_COMPAT", "http://example.invalid", "k", "m1", "e1", Map.of(), Map.of(), 1000, 1000
        );
        when(llmCallQueueService.parseOpenAiUsageFromJson(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 3, 2))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, null, null, null));
        when(tokenCountService.decideChatTokens(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(new TokenCountService.TokenDecision(6, 4, 10, null, "mock"));

        try (org.mockito.MockedConstruction<OpenAiCompatClient> mocked = org.mockito.Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (m, c) -> when(m.chatCompletionsOnce(org.mockito.ArgumentMatchers.any()))
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"\"}}]}")
        )) {
            Object r1 = callInstance(
                    gateway,
                    "callChatOnceSingleNoQueue",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class, int.class, Map.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, null, null, null, null, Map.of(), 1, Map.of()
            );
            Object r2 = callInstance(
                    gateway,
                    "callChatOnceSingleNoQueue",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class, int.class, Map.class
                    },
                    LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, null, null, null, null, Map.of(), 1, Map.of()
            );
            assertNotNull(r1);
            assertNotNull(r2);
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void directive_and_block_helpers_should_cover_guard_and_early_return_paths() throws Exception {
        assertFalse((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "vendor/"));
        assertFalse((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "vendor:"));

        List<ChatMessage> noUser = Arrays.asList(
                null,
                new ChatMessage(null, "x"),
                new ChatMessage("assistant", "a")
        );
        assertEquals(noUser, callStatic(
                "applyThinkingDirectiveToMessages",
                new Class[]{List.class, Boolean.class, String.class},
                noUser,
                Boolean.TRUE,
                "qwen3-32b"
        ));

        List<ChatMessage> userNotString = List.of(new ChatMessage("user", Map.of("text", "x")));
        assertEquals(userNotString, callStatic(
                "applyThinkingDirectiveToMessages",
                new Class[]{List.class, Boolean.class, String.class},
                userNotString,
                Boolean.TRUE,
                "qwen3-32b"
        ));

        List<ChatMessage> alreadyDirective = List.of(new ChatMessage("user", "hello /think"));
        assertEquals(alreadyDirective, callStatic(
                "applyThinkingDirectiveToMessages",
                new Class[]{List.class, Boolean.class, String.class},
                alreadyDirective,
                Boolean.TRUE,
                "qwen3-32b"
        ));

        assertEquals("&lt;reasoning_content x", callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, "&lt;reasoning_content x"));
        assertEquals("<reasoning_content x", callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, "<reasoning_content x"));
        assertEquals("<reasoning_content>abc", callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, "<reasoning_content>abc"));
        assertEquals("&lt;reasoning_content&gt;abc", callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, "&lt;reasoning_content&gt;abc"));

        assertEquals("&lt;think x", callStatic("removeClosedThinkBlocks", new Class[]{String.class}, "&lt;think x"));
        assertEquals("<think x", callStatic("removeClosedThinkBlocks", new Class[]{String.class}, "<think x"));
        assertEquals("<think>abc", callStatic("removeClosedThinkBlocks", new Class[]{String.class}, "<think>abc"));
        assertEquals("&lt;think&gt;abc", callStatic("removeClosedThinkBlocks", new Class[]{String.class}, "&lt;think&gt;abc"));

        String rrMulti = (String) callStatic(
                "removeClosedReasoningBlocks",
                new Class[]{String.class},
                "A<reasoning_content>x</reasoning_content>   B<reasoning_content>y</reasoning_content>  C"
        );
        assertEquals("ABC", rrMulti.replace(" ", ""));
        String rtMulti = (String) callStatic(
                "removeClosedThinkBlocks",
                new Class[]{String.class},
                "A<think>x</think>  B<think>y</think>   C"
        );
        assertEquals("ABC", rtMulti.replace(" ", ""));
    }

    @Test
    void stream_retry_helpers_should_cover_non_retriable_and_started_throw_paths() throws Exception {
        Method withStreamRetry = LlmGateway.class.getDeclaredMethod(
                "withStreamRetry",
                int.class,
                OpenAiCompatClient.ChatRequest.class,
                OpenAiCompatClient.class,
                OpenAiCompatClient.SseLineConsumer.class
        );
        withStreamRetry.setAccessible(true);

        OpenAiCompatClient.ChatRequest req = mock(OpenAiCompatClient.ChatRequest.class);
        OpenAiCompatClient.SseLineConsumer sink = line -> {
        };

        OpenAiCompatClient c1 = mock(OpenAiCompatClient.class);
        org.mockito.Mockito.doThrow(new IOException("HTTP 400 bad request"))
                .when(c1).chatCompletionsStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        try {
            withStreamRetry.invoke(null, 2, req, c1, sink);
            assertTrue(false);
        } catch (InvocationTargetException e) {
            assertTrue(String.valueOf(e.getCause().getMessage()).contains("HTTP 400"));
        }

        OpenAiCompatClient c2 = mock(OpenAiCompatClient.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer s = inv.getArgument(1);
            s.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}");
            throw new SocketTimeoutException("timeout-started");
        }).when(c2).chatCompletionsStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        try {
            withStreamRetry.invoke(null, 2, req, c2, sink);
            assertTrue(false);
        } catch (InvocationTargetException e) {
            assertTrue(String.valueOf(e.getCause().getMessage()).contains("timeout-started"));
        }
    }

    @Test
    void normalizeOpenAiCompatUsage_should_cover_null_total_and_sparse_metric_paths() throws Exception {
        LlmCallQueueService.UsageMetrics cOnly = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                null, 6, null
        );
        assertEquals(null, cOnly.promptTokens());
        assertEquals(6, cOnly.completionTokens());
        assertEquals(null, cOnly.totalTokens());

        LlmCallQueueService.UsageMetrics tOnly = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                null, null, 9
        );
        assertEquals(null, tOnly.promptTokens());
        assertEquals(null, tOnly.completionTokens());
        assertEquals(9, tOnly.totalTokens());

        LlmCallQueueService.UsageMetrics pOnly = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                4, null, null
        );
        assertEquals(4, pOnly.promptTokens());
        assertEquals(null, pOnly.completionTokens());
        assertEquals(null, pOnly.totalTokens());

        LlmCallQueueService.UsageMetrics pcNoTotal = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                2, 3, null
        );
        assertEquals(2, pcNoTotal.promptTokens());
        assertEquals(3, pcNoTotal.completionTokens());
        assertEquals(5, pcNoTotal.totalTokens());
    }

    @Test
    void extractStreamChunkStats_should_cover_non_object_delta_absent_and_text_only_paths() throws Exception {
        LlmGateway gateway = gateway();
        AtomicReference<LlmCallQueueService.UsageMetrics> ref = new AtomicReference<>();
        long[] outChars = new long[]{0L};

        callInstance(
                gateway,
                "extractStreamChunkStats",
                new Class[]{String.class, boolean.class, long[].class, AtomicReference.class},
                "{\"choices\":[{\"delta\":\"not-object\",\"text\":\"abc\"}],\"usage\":{\"prompt_tokens\":-1,\"completion_tokens\":2,\"total_tokens\":1}}",
                true,
                outChars,
                ref
        );
        assertEquals(3L, outChars[0]);
        assertNotNull(ref.get());
        assertEquals(null, ref.get().promptTokens());
        assertEquals(2, ref.get().completionTokens());
        assertEquals(1, ref.get().totalTokens());

        callInstance(
                gateway,
                "extractStreamChunkStats",
                new Class[]{String.class, boolean.class, long[].class, AtomicReference.class},
                "{\"choices\":[{\"delta\":{\"reasoning_content\":\"long-reason\",\"content\":\"\"},\"text\":\"\"}],\"prompt_tokens\":7,\"completion_tokens\":1,\"total_tokens\":8}",
                false,
                outChars,
                ref
        );
        assertEquals(3L, outChars[0]);
        assertNotNull(ref.get());
        assertEquals(7, ref.get().promptTokens());
        assertEquals(1, ref.get().completionTokens());
        assertEquals(8, ref.get().totalTokens());

        callInstance(
                gateway,
                "extractStreamChunkStats",
                new Class[]{String.class, boolean.class, long[].class, AtomicReference.class},
                "[]",
                true,
                outChars,
                ref
        );
        assertEquals(3L, outChars[0]);
    }

    @Test
    void extractStreamChunkText_should_cover_reasoning_only_content_only_and_empty_choices_paths() throws Exception {
        LlmGateway gateway = gateway();
        assertNull(callInstance(
                gateway,
                "extractStreamChunkText",
                new Class[]{String.class, boolean.class},
                "{\"choices\":[]}",
                true
        ));

        String reasoningOnly = (String) callInstance(
                gateway,
                "extractStreamChunkText",
                new Class[]{String.class, boolean.class},
                "{\"choices\":[{\"delta\":{\"reasoning_content\":\"abc\"}}]}",
                true
        );
        assertEquals("abc", reasoningOnly);

        String contentOnly = (String) callInstance(
                gateway,
                "extractStreamChunkText",
                new Class[]{String.class, boolean.class},
                "{\"choices\":[{\"delta\":{\"content\":\"xyz\"}}]}",
                true
        );
        assertEquals("xyz", contentOnly);

        String textFallback = (String) callInstance(
                gateway,
                "extractStreamChunkText",
                new Class[]{String.class, boolean.class},
                "{\"choices\":[{\"delta\":\"bad\",\"text\":\"fallback\"}]}",
                true
        );
        assertEquals("fallback", textFallback);
    }

    @Test
    void removeLabelMapFromEmbeddedJson_should_cover_scalar_suffix_and_non_object_taxonomy_paths() throws Exception {
        LlmGateway gateway = gateway();

        String scalarSuffix = "prefix\n\n123";
        assertEquals(
                scalarSuffix,
                callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, scalarSuffix)
        );

        String nonObjectTaxonomy = "prefix\n\n{\"label_taxonomy\":\"raw\",\"k\":1}";
        String out = (String) callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, nonObjectTaxonomy);
        assertTrue(out.contains("\"label_taxonomy\":\"raw\""));
        assertTrue(out.contains("\"k\":1"));

        String mixedArray = "lead\n\n[{\"label_taxonomy\":{\"label_map\":{\"a\":1},\"k\":1}},\"s\",1]";
        String outArray = (String) callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, mixedArray);
        assertFalse(outArray.contains("label_map"));
        assertTrue(outArray.contains("\"s\""));
    }

    @Test
    void supportsThinkingDirectiveModel_should_cover_raw_prefix_branches() throws Exception {
        assertFalse((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "vendor/qwen3-thinking-32b"));
        assertTrue((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "provider/qwen3-32b"));
        assertTrue((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "qwen-plus-2025-04-28/alias"));
        assertTrue((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "qwen-turbo-2025-04-28/alias"));
    }

    @Test
    void removeClosedReasoningBlocks_should_cover_guard_limit_and_escaped_precedence_paths() throws Exception {
        String withBothMarkers = "A<reasoning_content>x</reasoning_content>&lt;reasoning_content&gt;y&lt;/reasoning_content&gt;B";
        String cleaned = (String) callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, withBothMarkers);
        assertFalse(cleaned.contains("reasoning_content"));
        assertTrue(cleaned.startsWith("A"));
        assertTrue(cleaned.endsWith("B"));

        StringBuilder longChain = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longChain.append("<reasoning_content>").append(i).append("</reasoning_content>");
        }
        String emptied = (String) callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, longChain.toString());
        assertFalse(emptied.contains("reasoning_content"));

        assertEquals(" ", callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, " "));
    }

    @Test
    void removeClosedThinkBlocks_should_cover_guard_limit_and_escaped_precedence_paths() throws Exception {
        String withBothMarkers = "A<think>x</think>&lt;think&gt;y&lt;/think&gt;B";
        String cleaned = (String) callStatic("removeClosedThinkBlocks", new Class[]{String.class}, withBothMarkers);
        assertFalse(cleaned.contains("think"));
        assertTrue(cleaned.startsWith("A"));
        assertTrue(cleaned.endsWith("B"));

        StringBuilder longChain = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longChain.append("<think>").append(i).append("</think>");
        }
        String emptied = (String) callStatic("removeClosedThinkBlocks", new Class[]{String.class}, longChain.toString());
        assertFalse(emptied.contains("think"));

        assertEquals(" ", callStatic("removeClosedThinkBlocks", new Class[]{String.class}, " "));
    }

    @Test
    void string_and_key_helper_methods_should_cover_remaining_short_circuit_branches() throws Exception {
        assertEquals(-1, callStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, "abc", null, 0));
        assertEquals(-1, callStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, "abc", "", 99));

        assertEquals("\n/think", callStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, null, true, "qwen3-32b"));
        assertEquals("x\n/think", callStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, "x\n", true, "qwen3-32b"));

        assertEquals("", callStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, "", "think"));
        assertEquals("abc", callStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, "abc", null));
        assertEquals("abc", callStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, "abc", " "));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode obj = mapper.readTree("{\"a\":\"1\"}");
        assertEquals(1, callStatic("pickIntLoose", new Class[]{JsonNode.class, String[].class}, obj, new String[]{null, "a"}));
    }
}
