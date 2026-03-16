package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class LlmGatewayPrivateHelpersCoverageTest {

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
    void normalizeOpenAiCompatUsage_should_cover_key_combinations() throws Exception {
        assertNull(callStatic("normalizeOpenAiCompatUsage", new Class[]{Integer.class, Integer.class, Integer.class}, null, null, null));

        LlmCallQueueService.UsageMetrics m1 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                10, 2, 5
        );
        assertEquals(10, m1.promptTokens());
        assertEquals(5, m1.completionTokens());
        assertEquals(15, m1.totalTokens());

        LlmCallQueueService.UsageMetrics m2 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                10, 0, 20
        );
        assertEquals(10, m2.promptTokens());
        assertEquals(10, m2.completionTokens());
        assertEquals(20, m2.totalTokens());

        LlmCallQueueService.UsageMetrics m3 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                3, 4, 99
        );
        assertEquals(3, m3.promptTokens());
        assertEquals(4, m3.completionTokens());
        assertEquals(7, m3.totalTokens());

        LlmCallQueueService.UsageMetrics m4 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                4, null, 10
        );
        assertEquals(4, m4.promptTokens());
        assertEquals(6, m4.completionTokens());
        assertEquals(10, m4.totalTokens());

        LlmCallQueueService.UsageMetrics m5 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                7, null, 3
        );
        assertEquals(7, m5.promptTokens());
        assertEquals(3, m5.completionTokens());
        assertEquals(10, m5.totalTokens());

        LlmCallQueueService.UsageMetrics m6 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                null, 2, 9
        );
        assertEquals(7, m6.promptTokens());
        assertEquals(2, m6.completionTokens());
        assertEquals(9, m6.totalTokens());

        LlmCallQueueService.UsageMetrics m7 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                -1, -2, -3
        );
        assertNull(m7);
    }

    @Test
    void asIntLoose_and_pickIntLoose_should_cover_null_text_number_and_parse_failures() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode obj = mapper.readTree("{\"a\":\"12\",\"b\":\"3.7\",\"c\":\"x\",\"d\":8}");

        assertNull(callStatic("asIntLoose", new Class[]{JsonNode.class}, (Object) null));
        assertNull(callStatic("asIntLoose", new Class[]{JsonNode.class}, JsonNodeFactory.instance.missingNode()));
        assertNull(callStatic("asIntLoose", new Class[]{JsonNode.class}, JsonNodeFactory.instance.nullNode()));
        assertEquals(8, callStatic("asIntLoose", new Class[]{JsonNode.class}, obj.path("d")));
        assertEquals(12, callStatic("asIntLoose", new Class[]{JsonNode.class}, obj.path("a")));
        assertEquals(3, callStatic("asIntLoose", new Class[]{JsonNode.class}, obj.path("b")));
        assertNull(callStatic("asIntLoose", new Class[]{JsonNode.class}, obj.path("c")));
        assertNull(callStatic("asIntLoose", new Class[]{JsonNode.class}, new TextNode(" ")));

        assertNull(callStatic("pickIntLoose", new Class[]{JsonNode.class, String[].class}, null, new String[]{"a"}));
        assertNull(callStatic("pickIntLoose", new Class[]{JsonNode.class, String[].class}, mapper.readTree("[]"), new String[]{"a"}));
        assertNull(callStatic("pickIntLoose", new Class[]{JsonNode.class, String[].class}, obj, null));
        assertEquals(12, callStatic("pickIntLoose", new Class[]{JsonNode.class, String[].class}, obj, new String[]{"", "a"}));
        assertNull(callStatic("pickIntLoose", new Class[]{JsonNode.class, String[].class}, obj, new String[]{"z"}));
    }

    @Test
    void stream_extractors_should_cover_reasoning_content_text_and_usage_paths() throws Exception {
        LlmGateway gateway = gateway();
        AtomicReference<LlmCallQueueService.UsageMetrics> ref = new AtomicReference<>();
        long[] outChars = new long[]{0L};

        String payload = """
                {"usage":{"input_tokens":"11","output_tokens":"4","total_tokens":"15"},"choices":[{"delta":{"reasoning_content":"ab","content":"xyz"}}]}
                """.trim();
        callInstance(
                gateway,
                "extractStreamChunkStats",
                new Class[]{String.class, boolean.class, long[].class, AtomicReference.class},
                payload, true, outChars, ref
        );
        assertEquals(5L, outChars[0]);
        assertNotNull(ref.get());
        assertEquals(11, ref.get().promptTokens());
        assertEquals(4, ref.get().completionTokens());
        assertEquals(15, ref.get().totalTokens());

        String t1 = (String) callInstance(gateway, "extractStreamChunkText", new Class[]{String.class, boolean.class}, payload, true);
        assertEquals("abxyz", t1);
        String t2 = (String) callInstance(gateway, "extractStreamChunkText", new Class[]{String.class, boolean.class}, payload, false);
        assertEquals("xyz", t2);
        String t3 = (String) callInstance(
                gateway,
                "extractStreamChunkText",
                new Class[]{String.class, boolean.class},
                "{\"choices\":[{\"text\":\"hello\"}]}",
                true
        );
        assertEquals("hello", t3);
        assertNull(callInstance(gateway, "extractStreamChunkText", new Class[]{String.class, boolean.class}, "", true));
        assertNull(callInstance(gateway, "extractStreamChunkText", new Class[]{String.class, boolean.class}, "{bad", true));
    }

    @Test
    void removeLabelMapFromEmbeddedJson_should_cover_object_array_prefix_and_invalid_json() throws Exception {
        LlmGateway gateway = gateway();
        String rawObj = "trace\n\n{\"label_taxonomy\":{\"label_map\":{\"a\":1},\"keep\":2},\"k\":1}";
        String outObj = (String) callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, rawObj);
        assertTrue(outObj.contains("\"label_taxonomy\""));
        assertFalse(outObj.contains("label_map"));
        assertTrue(outObj.contains("\"keep\":2"));

        String rawArr = "x\n[{\"label_taxonomy\":{\"label_map\":{\"a\":1},\"t\":1}},{\"label_taxonomy\":{\"label_map\":{\"b\":1},\"t\":2}}]";
        String outArr = (String) callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, rawArr);
        assertFalse(outArr.contains("label_map"));
        assertTrue(outArr.contains("\"t\":1"));
        assertTrue(outArr.contains("\"t\":2"));

        String noJson = "plain text only";
        assertEquals(noJson, callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, noJson));
        assertEquals("  ", callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, "  "));
        assertEquals("x\n{bad", callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, "x\n{bad"));
    }

    @Test
    void sanitize_and_header_helpers_should_cover_edge_cases() throws Exception {
        assertNull(callStatic("sanitizeMarker", new Class[]{String.class}, (Object) null));
        assertEquals("", callStatic("sanitizeMarker", new Class[]{String.class}, "reasoning_content"));
        assertEquals("", callStatic("sanitizeMarker", new Class[]{String.class}, "<reasoning_content>"));
        assertEquals("", callStatic("sanitizeMarker", new Class[]{String.class}, "reasoning_content reasoning_content"));
        assertEquals("ok", callStatic("sanitizeMarker", new Class[]{String.class}, "ok"));

        Map<String, String> m1 = (Map<String, String>) callStatic("mergeHeaders", new Class[]{Map.class, Map.class}, Map.of("a", "1"), Map.of("a", "2", "b", "3"));
        assertEquals("2", m1.get("a"));
        assertEquals("3", m1.get("b"));
        assertEquals(Map.of("a", "1"), callStatic("mergeHeaders", new Class[]{Map.class, Map.class}, Map.of("a", "1"), Map.of()));
        assertEquals(Map.of("b", "2"), callStatic("mergeHeaders", new Class[]{Map.class, Map.class}, Map.of(), Map.of("b", "2")));
    }

    @Test
    void retriable_and_error_code_helpers_should_cover_message_and_exception_types() throws Exception {
        IOException timeoutMsg = new IOException("HTTP 429 too many");
        assertTrue((boolean) callStatic("isRetriable", new Class[]{Throwable.class}, timeoutMsg));
        assertEquals("429", callStatic("extractErrorCode", new Class[]{Throwable.class}, timeoutMsg));

        IOException serverError = new IOException("HTTP 500");
        assertTrue((boolean) callStatic("isRetriable", new Class[]{Throwable.class}, serverError));
        assertEquals("5xx", callStatic("extractErrorCode", new Class[]{Throwable.class}, serverError));

        IOException reset = new IOException("Connection reset by peer");
        assertTrue((boolean) callStatic("isRetriable", new Class[]{Throwable.class}, reset));
        assertEquals("reset", callStatic("extractErrorCode", new Class[]{Throwable.class}, reset));

        SocketTimeoutException ste = new SocketTimeoutException("timed out");
        assertTrue((boolean) callStatic("isRetriable", new Class[]{Throwable.class}, ste));
        assertEquals("timeout", callStatic("extractErrorCode", new Class[]{Throwable.class}, ste));

        ConnectException ce = new ConnectException("refused");
        assertTrue((boolean) callStatic("isRetriable", new Class[]{Throwable.class}, ce));
        assertEquals("connect", callStatic("extractErrorCode", new Class[]{Throwable.class}, ce));

        Exception nested = new Exception("x", new IOException("HTTP 429 too many requests"));
        assertTrue((boolean) callStatic("isRetriable", new Class[]{Throwable.class}, nested));
        assertEquals("429", callStatic("extractErrorCode", new Class[]{Throwable.class}, nested));

        IllegalArgumentException non = new IllegalArgumentException("bad request");
        assertFalse((boolean) callStatic("isRetriable", new Class[]{Throwable.class}, non));
        assertEquals("", callStatic("extractErrorCode", new Class[]{Throwable.class}, non));
    }

    @Test
    void sanitizeMessagesForTrace_should_cover_string_parts_and_non_map_parts() throws Exception {
        LlmGateway gateway = gateway();
        List<ChatMessage> in = List.of(
                ChatMessage.user("TRACE a\nhello\n\n{\"label_taxonomy\":{\"label_map\":{\"k\":1},\"x\":1}}"),
                new ChatMessage("user", List.of(
                        Map.of("type", "text", "text", "TRACE b\nworld\n\n{\"label_taxonomy\":{\"label_map\":{\"k\":2},\"y\":1}}"),
                        "raw-part"
                )),
                new ChatMessage("assistant", 123)
        );

        @SuppressWarnings("unchecked")
        List<ChatMessage> out = (List<ChatMessage>) callInstance(gateway, "sanitizeMessagesForTrace", new Class[]{List.class}, in);
        assertEquals(3, out.size());
        assertFalse(String.valueOf(out.get(0).content()).contains("TRACE "));
        assertFalse(String.valueOf(out.get(0).content()).contains("label_map"));
        assertTrue(String.valueOf(out.get(0).content()).contains("\"x\":1"));
        assertTrue(String.valueOf(out.get(1).content()).contains("raw-part"));
    }

    @Test
    void thinking_directive_helpers_should_cover_supported_and_unsupported_models() throws Exception {
        assertFalse((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, (Object) null));
        assertFalse((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, ""));
        assertFalse((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "qwen3-thinking"));
        assertTrue((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "qwen3-32b"));
        assertTrue((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "provider/qwen-plus-2025-04-28"));
        assertTrue((boolean) callStatic("supportsThinkingDirectiveModel", new Class[]{String.class}, "x:qwen-turbo-2025-04-28"));

        assertEquals("hi\n/think", callStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, "hi", true, "qwen3-32b"));
        assertEquals("hi/no", callStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, "hi/no", false, "not-supported"));
        assertEquals("/no_think", callStatic("applyThinkingDirective", new Class[]{String.class, boolean.class, String.class}, "/no_think", true, "qwen3-32b"));

        List<ChatMessage> messages = List.of(ChatMessage.system("s"), ChatMessage.user("u"));
        @SuppressWarnings("unchecked")
        List<ChatMessage> patched = (List<ChatMessage>) callStatic(
                "applyThinkingDirectiveToMessages",
                new Class[]{List.class, Boolean.class, String.class},
                messages, Boolean.TRUE, "qwen3-32b"
        );
        assertTrue(String.valueOf(patched.get(1).content()).contains("/think"));

        @SuppressWarnings("unchecked")
        List<ChatMessage> untouched = (List<ChatMessage>) callStatic(
                "applyThinkingDirectiveToMessages",
                new Class[]{List.class, Boolean.class, String.class},
                new ArrayList<>(List.of(ChatMessage.system("s"))), Boolean.TRUE, "qwen3-32b"
        );
        assertEquals("s", String.valueOf(untouched.get(0).content()));
    }

    @Test
    void text_cleanup_helpers_should_cover_index_strip_and_reasoning_paths() throws Exception {
        assertEquals(-1, callStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, null, "a", 0));
        assertEquals(0, callStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, "abc", "", 0));
        assertEquals(2, callStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, "xxAbCxx", "abc", 0));
        assertEquals(-1, callStatic("indexOfIgnoreCase", new Class[]{String.class, String.class, int.class}, "abc", "abcd", 0));

        assertEquals(3, callStatic("minPositive", new Class[]{int.class, int.class}, -1, 3));
        assertEquals(2, callStatic("minPositive", new Class[]{int.class, int.class}, 2, -1));
        assertEquals(1, callStatic("minPositive", new Class[]{int.class, int.class}, 1, 4));

        assertEquals("A", callStatic("stripThinkBlocks", new Class[]{String.class}, "A<think>x</think>"));
        assertEquals("A", callStatic("stripThinkBlocks", new Class[]{String.class}, "A&lt;think&gt;x&lt;/think&gt;"));
        assertEquals("A", callStatic("removeClosedThinkBlocks", new Class[]{String.class}, "A<think>x</think>"));
        assertEquals("A", callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, "A<reasoning_content>x</reasoning_content>"));
        assertEquals("A ", callStatic("stripReasoningArtifacts", new Class[]{String.class}, "A reasoning_content"));
        assertEquals("AB", callStatic("removeMarkerWordIgnoreCase", new Class[]{String.class, String.class}, "Areasoning_contentB", "reasoning_content"));
    }

    @Test
    void fallback_filter_and_estimate_helpers_should_cover_key_branches() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiProvidersConfigService.ResolvedProvider p1 = new AiProvidersConfigService.ResolvedProvider(
                "p1", "OPENAI_COMPAT", "http://example.invalid", "k", "m1", "e1", Map.of(), Map.of(), 1000, 1000
        );
        AiProvidersConfigService.ResolvedProvider p2 = new AiProvidersConfigService.ResolvedProvider(
                "p2", "OPENAI_COMPAT", "http://example.invalid", "k", "", "e2", Map.of(), Map.of(), 1000, 1000
        );
        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("p1", "p2"));
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p1);
        when(aiProvidersConfigService.resolveProvider("p2")).thenReturn(p2);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                mock(AiEmbeddingService.class),
                mock(AiRerankService.class),
                mock(LlmCallQueueService.class),
                mock(LlmRoutingService.class),
                mock(LlmRoutingTelemetryService.class),
                mock(TokenCountService.class)
        );

        String fallback = (String) callInstance(
                gateway,
                "pickFallbackProviderId",
                new Class[]{java.util.function.Function.class},
                (java.util.function.Function<AiProvidersConfigService.ResolvedProvider, String>) AiProvidersConfigService.ResolvedProvider::defaultChatModel
        );
        assertEquals("p1", fallback);

        Map<String, Object> filtered = (Map<String, Object>) callStatic(
                "filterExtraBody",
                new Class[]{AiProvidersConfigService.ResolvedProvider.class, Map.class},
                p1,
                Map.of("vl_high_resolution_images", true, "x", 1, " ", 2)
        );
        assertNotNull(filtered);
        assertFalse(filtered.containsKey("vl_high_resolution_images"));
        assertTrue(filtered.containsKey("x"));

        AiProvidersConfigService.ResolvedProvider dash = new AiProvidersConfigService.ResolvedProvider(
                "pd", "OPENAI_COMPAT", "https://dashscope.aliyuncs.com/compatible-mode/v1", "k", "m", "e", Map.of(), Map.of(), 1000, 1000
        );
        Map<String, Object> kept = (Map<String, Object>) callStatic(
                "filterExtraBody",
                new Class[]{AiProvidersConfigService.ResolvedProvider.class, Map.class},
                dash,
                Map.of("vl_high_resolution_images", true)
        );
        assertTrue(kept.containsKey("vl_high_resolution_images"));

        int tokens = (int) callStatic("estimateInputTokens", new Class[]{List.class}, List.of(
                ChatMessage.user("abc"),
                new ChatMessage("user", List.of(Map.of("text", "xy"), Map.of("image_url", Map.of("url", "u"))))
        ));
        assertTrue(tokens > 0);
        assertEquals(0, callStatic("estimateInputTokens", new Class[]{List.class}, List.of()));
    }

    @Test
    void withRetry_should_cover_success_retry_and_non_retriable_paths() throws Exception {
        Class<?> supplierType = Class.forName("com.example.EnterpriseRagCommunity.service.ai.LlmGateway$CheckedSupplier");
        Method withRetry = LlmGateway.class.getDeclaredMethod("withRetry", int.class, supplierType);
        withRetry.setAccessible(true);

        Object ok = Proxy.newProxyInstance(supplierType.getClassLoader(), new Class[]{supplierType}, (proxy, method, args) -> "ok");
        assertEquals("ok", withRetry.invoke(null, 1, ok));

        AtomicInteger counter = new AtomicInteger(0);
        InvocationHandler retryHandler = (proxy, method, args) -> {
            if (counter.incrementAndGet() == 1) throw new IOException("HTTP 429");
            return "done";
        };
        Object retry = Proxy.newProxyInstance(supplierType.getClassLoader(), new Class[]{supplierType}, retryHandler);
        assertEquals("done", withRetry.invoke(null, 2, retry));

        Object bad = Proxy.newProxyInstance(
                supplierType.getClassLoader(),
                new Class[]{supplierType},
                (proxy, method, args) -> { throw new IllegalArgumentException("bad"); }
        );
        try {
            withRetry.invoke(null, 2, bad);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(String.valueOf(e.getCause()).contains("bad"));
        }
    }

    @Test
    void stream_retry_helpers_should_cover_started_and_non_started_paths() throws Exception {
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
        OpenAiCompatClient.SseLineConsumer sink = line -> {};

        OpenAiCompatClient c1 = mock(OpenAiCompatClient.class);
        AtomicInteger n1 = new AtomicInteger(0);
        doAnswer(inv -> {
            if (n1.incrementAndGet() == 1) throw new IOException("HTTP 429");
            return null;
        }).when(c1).chatCompletionsStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        withStreamRetry.invoke(null, 2, req, c1, sink);

        OpenAiCompatClient c2 = mock(OpenAiCompatClient.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer wrapped = inv.getArgument(1);
            wrapped.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}");
            throw new IOException("HTTP 429");
        }).when(c2).chatCompletionsStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        try {
            withStreamRetry.invoke(null, 2, req, c2, sink);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(String.valueOf(e.getCause()).contains("HTTP 429"));
        }

        OpenAiCompatClient c3 = mock(OpenAiCompatClient.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer wrapped = inv.getArgument(1);
            wrapped.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}");
            throw new IOException("HTTP 429");
        }).when(c3).chatCompletionsStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        try {
            callStreamWithRetry.invoke(null, 2, req, c3, sink);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(String.valueOf(e.getCause()).contains("HTTP 429"));
        }

        OpenAiCompatClient c4 = mock(OpenAiCompatClient.class);
        doAnswer(inv -> { throw new IllegalArgumentException("bad"); })
                .when(c4).chatCompletionsStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        try {
            callStreamWithRetry.invoke(null, 2, req, c4, sink);
            assertTrue(false);
        } catch (Exception e) {
            assertTrue(String.valueOf(e.getCause()).contains("bad"));
        }
    }

    @Test
    void remove_closed_blocks_should_cover_unclosed_and_escaped_paths() throws Exception {
        String r1 = (String) callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, "a<reasoning_content>x</reasoning_content>b");
        assertEquals("ab", r1);
        String r2 = (String) callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, "a&lt;reasoning_content&gt;x&lt;/reasoning_content&gt;b");
        assertEquals("ab", r2);
        String r3 = (String) callStatic("removeClosedReasoningBlocks", new Class[]{String.class}, "a<reasoning_content x");
        assertEquals("a<reasoning_content x", r3);

        String t1 = (String) callStatic("removeClosedThinkBlocks", new Class[]{String.class}, "a<think>x</think>b");
        assertEquals("ab", t1);
        String t2 = (String) callStatic("removeClosedThinkBlocks", new Class[]{String.class}, "a&lt;think&gt;x&lt;/think&gt;b");
        assertEquals("ab", t2);
        String t3 = (String) callStatic("removeClosedThinkBlocks", new Class[]{String.class}, "a<think x");
        assertEquals("a<think x", t3);
    }

    @Test
    void callChatOnceSingle_should_cover_queue_supplier_extractor_and_usage_fallback_paths() throws Exception {
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
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 3, 2))
                .thenReturn(null)
                .thenReturn(null);
        when(tokenCountService.decideChatTokens(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(null, new TokenCountService.TokenDecision(10, 11, 21, null, "mock"));

        when(llmCallQueueService.call(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.CheckedTaskSupplier.class),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = (LlmCallQueueService.CheckedTaskSupplier<String>) inv.getArgument(4);
            @SuppressWarnings("unchecked")
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = (LlmCallQueueService.ResultMetricsExtractor<String>) inv.getArgument(5);
            LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
            when(task.id()).thenReturn("task-1");
            String raw = supplier.get(task);
            extractor.extract(raw);
            return raw;
        });

        try (org.mockito.MockedConstruction<OpenAiCompatClient> mocked = org.mockito.Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (m, c) -> when(m.chatCompletionsOnce(org.mockito.ArgumentMatchers.any()))
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
        )) {
            Object r1 = callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.1, null, null, null, null, null, Map.of("foo", "bar"), 1,
                    new AtomicReference<String>(), Map.of("X-Test", "1")
            );
            assertNotNull(r1);

            Object r2 = callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, null, null, Boolean.FALSE, null, Map.of(), 1,
                    new AtomicReference<String>(), Map.of()
            );
            assertNotNull(r2);
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void callChatStreamSingle_should_cover_stream_wrapped_consumer_branches() throws Exception {
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

        when(llmCallQueueService.call(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.CheckedTaskSupplier.class),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> supplier =
                    (LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>) inv.getArgument(4);
            return supplier.get(mock(LlmCallQueueService.TaskHandle.class));
        });

        OpenAiCompatClient.SseLineConsumer sink = line -> {};
        String payload = "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"r\",\"content\":\"x\"}}],\"usage\":{\"input_tokens\":3,\"output_tokens\":2,\"total_tokens\":5}}";

        try (org.mockito.MockedConstruction<OpenAiCompatClient> mocked = org.mockito.Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (m, c) -> doAnswer(inv -> {
                    OpenAiCompatClient.SseLineConsumer wrapped = inv.getArgument(1);
                    wrapped.onLine(payload);
                    wrapped.onLine("data: [DONE]");
                    return null;
                }).when(m).chatCompletionsStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())
        )) {
            Object u1 = callInstance(
                    gateway,
                    "callChatStreamSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Boolean.class, Integer.class, Map.class,
                            OpenAiCompatClient.SseLineConsumer.class, int.class, AtomicReference.class
                    },
                    LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.1, null, null, null, Map.of(), sink, 1, new AtomicReference<String>()
            );
            assertNotNull(u1);

            Object u2 = callInstance(
                    gateway,
                    "callChatStreamSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Boolean.class, Integer.class, Map.class,
                            OpenAiCompatClient.SseLineConsumer.class, int.class, AtomicReference.class
                    },
                    LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, Boolean.FALSE, null, Map.of(), sink, 1, new AtomicReference<String>()
            );
            assertNotNull(u2);
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void stream_extractors_should_cover_additional_empty_and_text_fallback_paths() throws Exception {
        LlmGateway gateway = gateway();
        AtomicReference<LlmCallQueueService.UsageMetrics> ref = new AtomicReference<>();
        long[] outChars = new long[]{0L};

        callInstance(
                gateway,
                "extractStreamChunkStats",
                new Class[]{String.class, boolean.class, long[].class, AtomicReference.class},
                "{\"prompt_tokens\":\"2\",\"completion_tokens\":\"3\"}", false, outChars, ref
        );
        assertNotNull(ref.get());
        assertEquals(2, ref.get().promptTokens());
        assertEquals(3, ref.get().completionTokens());
        assertEquals(5, ref.get().totalTokens());

        String onlyReasoning = (String) callInstance(
                gateway,
                "extractStreamChunkText",
                new Class[]{String.class, boolean.class},
                "{\"choices\":[{\"delta\":{\"reasoning_content\":\"reasoning_content\"}}]}",
                true
        );
        assertNull(onlyReasoning);

        String onlyReasoningNo = (String) callInstance(
                gateway,
                "extractStreamChunkText",
                new Class[]{String.class, boolean.class},
                "{\"choices\":[{\"delta\":{\"reasoning_content\":\"abc\"}}]}",
                false
        );
        assertNull(onlyReasoningNo);

        String onlyContent = (String) callInstance(
                gateway,
                "extractStreamChunkText",
                new Class[]{String.class, boolean.class},
                "{\"choices\":[{\"delta\":{\"content\":\"reasoning_content hello\"}}]}",
                false
        );
        assertEquals(" hello", onlyContent);

        String fallbackText = (String) callInstance(
                gateway,
                "extractStreamChunkText",
                new Class[]{String.class, boolean.class},
                "{\"choices\":[{\"delta\":{},\"text\":\"t\"}]}",
                true
        );
        assertEquals("t", fallbackText);

        String markerOnly = (String) callInstance(
                gateway,
                "extractStreamChunkText",
                new Class[]{String.class, boolean.class},
                "{\"choices\":[{\"delta\":{\"content\":\"reasoning_content\"}}]}",
                false
        );
        assertNull(markerOnly);

        String escapedMarker = (String) callInstance(
                gateway,
                "extractStreamChunkText",
                new Class[]{String.class, boolean.class},
                "{\"choices\":[{\"delta\":{\"reasoning_content\":\"&lt;reasoning_content&gt;\",\"content\":\"hello\"}}]}",
                true
        );
        assertEquals("hello", escapedMarker);
    }

    @Test
    void callChatOnceSingleNoQueue_should_cover_usage_partial_and_tokenizer_override() throws Exception {
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
                "p1", "OPENAI_COMPAT", "http://example.invalid", "k", "m1", "e1", Map.of("h", "1"), Map.of(), 1000, 1000
        );

        when(llmCallQueueService.parseOpenAiUsageFromJson(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(null, 2, null, null));
        when(tokenCountService.decideChatTokens(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(new TokenCountService.TokenDecision(9, 8, 17, null, "mock"));

        try (org.mockito.MockedConstruction<OpenAiCompatClient> mocked = org.mockito.Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (m, c) -> when(m.chatCompletionsOnce(org.mockito.ArgumentMatchers.any()))
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"<think>x</think>ok\"}}]}")
        )) {
            Object r = callInstance(
                    gateway,
                    "callChatOnceSingleNoQueue",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, Map.class
                    },
                    LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, null, null, Boolean.FALSE, null, Map.of("vl_high_resolution_images", true), 1, Map.of("x", "2")
            );
            assertNotNull(r);
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void callChatOnceSingle_should_cover_tokenizer_enabled_disabled_and_output_paths() throws Exception {
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

        when(llmCallQueueService.call(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.CheckedTaskSupplier.class),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = (LlmCallQueueService.CheckedTaskSupplier<String>) inv.getArgument(4);
            @SuppressWarnings("unchecked")
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = (LlmCallQueueService.ResultMetricsExtractor<String>) inv.getArgument(5);
            LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
            when(task.id()).thenReturn("task-x");
            String raw = supplier.get(task);
            extractor.extract(raw);
            return raw;
        });

        when(tokenCountService.decideChatTokens(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(null, new TokenCountService.TokenDecision(12, 13, 25, null, "mock"));

        try (org.mockito.MockedConstruction<OpenAiCompatClient> mocked = org.mockito.Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (m, c) -> when(m.chatCompletionsOnce(org.mockito.ArgumentMatchers.any()))
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4,\"total_tokens\":7}}")
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"<think>x</think>ok\"}}]}")
        )) {
            when(llmCallQueueService.parseOpenAiUsageFromJson(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(new LlmCallQueueService.UsageMetrics(3, 4, 7, 4))
                    .thenReturn(new LlmCallQueueService.UsageMetrics(3, 4, 7, 4))
                    .thenReturn(null)
                    .thenReturn(null);

            Object r1 = callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.1, null, null, null, Boolean.TRUE, null, Map.of(), 1,
                    null, Map.of()
            );
            assertNotNull(r1);

            Object r2 = callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, null, null, Boolean.FALSE, null, Map.of("x", 1), 1,
                    new AtomicReference<String>(), Map.of("x-h", "1")
            );
            assertNotNull(r2);
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void callChatOnceSingle_should_cover_report_output_variants_and_lambda_usage_fallback() throws Exception {
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

        List<String> outputs = new ArrayList<>();
        when(llmCallQueueService.call(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.CheckedTaskSupplier.class),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = (LlmCallQueueService.CheckedTaskSupplier<String>) inv.getArgument(4);
            @SuppressWarnings("unchecked")
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = (LlmCallQueueService.ResultMetricsExtractor<String>) inv.getArgument(5);
            LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
            when(task.id()).thenReturn("task-z");
            doAnswer(a -> {
                outputs.add(String.valueOf(a.getArguments()[0]));
                return null;
            }).when(task).reportOutput(org.mockito.ArgumentMatchers.any());
            String raw = supplier.get(task);
            extractor.extract(raw);
            return raw;
        });

        when(llmCallQueueService.parseOpenAiUsageFromJson(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1))
                .thenReturn(new LlmCallQueueService.UsageMetrics(2, 1, 3, 1))
                .thenReturn(new LlmCallQueueService.UsageMetrics(2, 1, 3, 1));

        try (org.mockito.MockedConstruction<OpenAiCompatClient> mocked = org.mockito.Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (m, c) -> when(m.chatCompletionsOnce(org.mockito.ArgumentMatchers.any()))
                        .thenReturn("hello")
                        .thenReturn("{\"choices\":[{\"message\":{}}]}")
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
        )) {
            Object r1 = callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.1, null, null, null, Boolean.TRUE, null, Map.of(), 1,
                    null, Map.of()
            );
            assertNotNull(r1);

            Object r2 = callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, null, null, Boolean.FALSE, null, Map.of(), 1,
                    null, Map.of()
            );
            assertNotNull(r2);

            Object r3 = callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.3, null, null, null, null, null, Map.of(), 1,
                    null, Map.of()
            );
            assertNotNull(r3);
            assertTrue(mocked.constructed().size() >= 1);
        }

        assertTrue(outputs.contains("hello"));
        assertTrue(outputs.size() >= 3);
    }

    @Test
    void extractStreamChunkStats_should_cover_root_usage_alias_includeReasoning_and_invalid_payload() throws Exception {
        LlmGateway gateway = gateway();
        AtomicReference<LlmCallQueueService.UsageMetrics> ref = new AtomicReference<>();
        long[] outChars = new long[]{0L};

        callInstance(
                gateway,
                "extractStreamChunkStats",
                new Class[]{String.class, boolean.class, long[].class, AtomicReference.class},
                "{\"promptTokens\":\"6\",\"completionTokens\":\"4\",\"choices\":[{\"delta\":{\"reasoning_content\":\"abc\",\"content\":\"xy\"},\"text\":\"z\"}]}", false, outChars, ref
        );
        assertNotNull(ref.get());
        assertEquals(6, ref.get().promptTokens());
        assertEquals(4, ref.get().completionTokens());
        assertEquals(10, ref.get().totalTokens());
        assertEquals(3L, outChars[0]);

        callInstance(
                gateway,
                "extractStreamChunkStats",
                new Class[]{String.class, boolean.class, long[].class, AtomicReference.class},
                "{\"usage\":{},\"choices\":[{\"delta\":{\"reasoning_content\":\"ab\",\"content\":\"cd\"},\"text\":\"e\"}]}", true, outChars, ref
        );
        assertEquals(8L, outChars[0]);

        callInstance(
                gateway,
                "extractStreamChunkStats",
                new Class[]{String.class, boolean.class, long[].class, AtomicReference.class},
                "{\"choices\":[]}", true, outChars, ref
        );
        assertEquals(8L, outChars[0]);

        callInstance(
                gateway,
                "extractStreamChunkStats",
                new Class[]{String.class, boolean.class, long[].class, AtomicReference.class},
                "{bad", true, outChars, ref
        );
        assertEquals(8L, outChars[0]);
    }

    @Test
    void extractAssistantContent_and_sanitizeMarker_should_cover_text_fallback_invalid_json_and_marker_variants() throws Exception {
        LlmGateway gateway = gateway();

        assertEquals("", callInstance(gateway, "extractAssistantContent", new Class[]{String.class}, (Object) null));
        assertEquals("", callInstance(gateway, "extractAssistantContent", new Class[]{String.class}, "  "));
        assertEquals(
                "txt-only",
                callInstance(
                        gateway,
                        "extractAssistantContent",
                        new Class[]{String.class},
                        "{\"choices\":[{\"message\":{},\"text\":\"txt-only\"}]}"
                )
        );
        assertEquals(
                "{bad",
                callInstance(gateway, "extractAssistantContent", new Class[]{String.class}, "{bad")
        );

        assertEquals(" ", callStatic("sanitizeMarker", new Class[]{String.class}, " "));
        assertEquals("", callStatic("sanitizeMarker", new Class[]{String.class}, " Reasoning_Content "));
        assertEquals("", callStatic("sanitizeMarker", new Class[]{String.class}, "</reasoning_content>"));
        assertEquals("", callStatic("sanitizeMarker", new Class[]{String.class}, "&lt;/reasoning_content&gt;"));
    }

    @Test
    void sanitize_and_estimate_helpers_should_cover_null_empty_and_non_text_parts() throws Exception {
        LlmGateway gateway = gateway();

        assertNull(callInstance(gateway, "sanitizeMessagesForTrace", new Class[]{List.class}, (Object) null));
        assertEquals(List.of(), callInstance(gateway, "sanitizeMessagesForTrace", new Class[]{List.class}, List.of()));

        List<ChatMessage> mixed = new ArrayList<>();
        mixed.add(null);
        mixed.add(new ChatMessage("assistant", null));
        mixed.add(new ChatMessage("assistant", 1234));
        mixed.add(new ChatMessage("user", List.of(
                Map.of(1, "x"),
                Map.of("k", "v"),
                7
        )));

        @SuppressWarnings("unchecked")
        List<ChatMessage> cleaned = (List<ChatMessage>) callInstance(gateway, "sanitizeMessagesForTrace", new Class[]{List.class}, mixed);
        assertEquals(3, cleaned.size());
        assertEquals(null, cleaned.get(0).content());
        assertEquals(1234, cleaned.get(1).content());

        assertNull(callInstance(gateway, "stripTraceLines", new Class[]{String.class}, (Object) null));
        assertEquals(" ", callInstance(gateway, "stripTraceLines", new Class[]{String.class}, " "));
        assertEquals("a\nb", callInstance(gateway, "stripTraceLines", new Class[]{String.class}, "TRACE x\na\nb"));

        assertEquals(0, callStatic("estimateInputTokens", new Class[]{List.class}, (Object) null));
        int tokens = (int) callStatic(
                "estimateInputTokens",
                new Class[]{List.class},
                List.of(
                        new ChatMessage(null, null),
                        new ChatMessage("u", Arrays.asList(1, null, Map.of("image_url", "not-map"))),
                        new ChatMessage("u", Map.of("k", "v"))
                )
        );
        assertTrue(tokens > 0);
    }

    @Test
    void callChatStreamSingle_should_cover_null_non_data_and_empty_delta_paths() throws Exception {
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

        when(llmCallQueueService.call(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.CheckedTaskSupplier.class),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> supplier =
                    (LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics>) inv.getArgument(4);
            return supplier.get(mock(LlmCallQueueService.TaskHandle.class));
        });

        try (org.mockito.MockedConstruction<OpenAiCompatClient> mocked = org.mockito.Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (m, c) -> doAnswer(inv -> {
                    OpenAiCompatClient.SseLineConsumer wrapped = inv.getArgument(1);
                    wrapped.onLine(null);
                    wrapped.onLine("event: ping");
                    wrapped.onLine("data: [DONE]");
                    wrapped.onLine("data: {\"choices\":[{\"delta\":{}}]}");
                    return null;
                }).when(m).chatCompletionsStream(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())
        )) {
            Object usage = callInstance(
                    gateway,
                    "callChatStreamSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Boolean.class, Integer.class, Map.class,
                            OpenAiCompatClient.SseLineConsumer.class, int.class, AtomicReference.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.1, null, Boolean.TRUE, null, Map.of(), (OpenAiCompatClient.SseLineConsumer) line -> {}, 1, null
            );
            assertNotNull(usage);
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void callChatOnceSingle_should_cover_blank_raw_output_branch() throws Exception {
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
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1));

        List<String> outputs = new ArrayList<>();
        when(llmCallQueueService.call(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.CheckedTaskSupplier.class),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = (LlmCallQueueService.CheckedTaskSupplier<String>) inv.getArgument(4);
            @SuppressWarnings("unchecked")
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = (LlmCallQueueService.ResultMetricsExtractor<String>) inv.getArgument(5);
            LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
            doAnswer(a -> {
                outputs.add(String.valueOf(a.getArguments()[0]));
                return null;
            }).when(task).reportOutput(org.mockito.ArgumentMatchers.any());
            String raw = supplier.get(task);
            extractor.extract(raw);
            return raw;
        });

        try (org.mockito.MockedConstruction<OpenAiCompatClient> mocked = org.mockito.Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (m, c) -> when(m.chatCompletionsOnce(org.mockito.ArgumentMatchers.any()))
                        .thenReturn("   ")
        )) {
            Object r = callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, null, null, null, null, Map.of(), 1, null, Map.of()
            );
            assertNotNull(r);
            assertTrue(mocked.constructed().size() >= 1);
        }
        assertTrue(outputs.stream().anyMatch(String::isEmpty));
    }

    @Test
    void callChatOnceSingle_should_cover_dashscope_and_output_branch_matrix() throws Exception {
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

        AiProvidersConfigService.ResolvedProvider dashProvider = new AiProvidersConfigService.ResolvedProvider(
                "pds", "OPENAI_COMPAT", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", "k", "m1", "e1", Map.of(), Map.of(), 1000, 1000
        );

        when(llmCallQueueService.parseOpenAiUsageFromJson(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 1, 2, 1))
                .thenReturn(new LlmCallQueueService.UsageMetrics(2, 1, 3, 1))
                .thenReturn(new LlmCallQueueService.UsageMetrics(2, 1, 3, 1))
                .thenReturn(new LlmCallQueueService.UsageMetrics(3, 1, 4, 1))
                .thenReturn(new LlmCallQueueService.UsageMetrics(3, 1, 4, 1));

        List<String> outputs = new ArrayList<>();
        when(llmCallQueueService.call(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.CheckedTaskSupplier.class),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = (LlmCallQueueService.CheckedTaskSupplier<String>) inv.getArgument(4);
            @SuppressWarnings("unchecked")
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = (LlmCallQueueService.ResultMetricsExtractor<String>) inv.getArgument(5);
            LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
            doAnswer(a -> {
                outputs.add(String.valueOf(a.getArguments()[0]));
                return null;
            }).when(task).reportOutput(org.mockito.ArgumentMatchers.any());
            String raw = supplier.get(task);
            extractor.extract(raw);
            return raw;
        });

        try (org.mockito.MockedConstruction<OpenAiCompatClient> mocked = org.mockito.Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (m, c) -> when(m.chatCompletionsOnce(org.mockito.ArgumentMatchers.any()))
                        .thenReturn("RAW_TEXT")
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"<think>x</think>ok\"}}]}")
        )) {
            callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, dashProvider, "m1", List.of(ChatMessage.user("hi")),
                    0.1, null, null, null, Boolean.TRUE, null, Map.of("vl_high_resolution_images", true), 1, null, Map.of()
            );
            callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, dashProvider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, null, null, Boolean.TRUE, null, Map.of(), 1, null, Map.of()
            );
            callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, dashProvider, "m1", List.of(ChatMessage.user("hi")),
                    0.3, null, null, null, Boolean.FALSE, null, Map.of(), 1, null, Map.of()
            );
            assertTrue(mocked.constructed().size() >= 1);
        }

        assertTrue(outputs.stream().anyMatch("RAW_TEXT"::equals));
        assertTrue(outputs.size() >= 3);
        assertTrue(outputs.stream().anyMatch(s -> s != null && !s.isBlank()));
    }

    @Test
    void callChatOnceSingle_should_cover_partial_usage_branches_with_null_token_service() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                null
        );

        AiProvidersConfigService.ResolvedProvider provider = new AiProvidersConfigService.ResolvedProvider(
                "p1", "OPENAI_COMPAT", "http://example.invalid", "k", "m1", "e1", Map.of(), Map.of(), 1000, 1000
        );

        when(llmCallQueueService.call(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.CheckedTaskSupplier.class),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = (LlmCallQueueService.CheckedTaskSupplier<String>) inv.getArgument(4);
            @SuppressWarnings("unchecked")
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = (LlmCallQueueService.ResultMetricsExtractor<String>) inv.getArgument(5);
            LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
            when(task.id()).thenReturn("task-usage");
            String raw = supplier.get(task);
            extractor.extract(raw);
            return raw;
        });

        when(llmCallQueueService.parseOpenAiUsageFromJson(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(5, null, 9, null))
                .thenReturn(new LlmCallQueueService.UsageMetrics(7, null, 10, null))
                .thenReturn(new LlmCallQueueService.UsageMetrics(null, 4, 9, null))
                .thenReturn(new LlmCallQueueService.UsageMetrics(null, 3, 8, null));

        try (org.mockito.MockedConstruction<OpenAiCompatClient> mocked = org.mockito.Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (m, c) -> when(m.chatCompletionsOnce(org.mockito.ArgumentMatchers.any()))
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"alpha\"}}]}")
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"beta\"}}]}")
        )) {
            Object r1 = callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, null, null, Boolean.TRUE, null, Map.of(), 1, null, Map.of()
            );
            Object r2 = callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.3, null, null, null, Boolean.TRUE, null, Map.of(), 1, null, Map.of()
            );
            assertNotNull(r1);
            assertNotNull(r2);
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void callChatOnceSingle_should_cover_null_raw_and_total_tokens_fallback_branches() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiEmbeddingService aiEmbeddingService = mock(AiEmbeddingService.class);
        AiRerankService aiRerankService = mock(AiRerankService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingTelemetryService llmRoutingTelemetryService = mock(LlmRoutingTelemetryService.class);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                aiEmbeddingService,
                aiRerankService,
                llmCallQueueService,
                llmRoutingService,
                llmRoutingTelemetryService,
                null
        );

        AiProvidersConfigService.ResolvedProvider provider = new AiProvidersConfigService.ResolvedProvider(
                "p1", "OPENAI_COMPAT", "http://example.invalid", "k", "m1", "e1", Map.of(), Map.of(), 1000, 1000
        );

        when(llmCallQueueService.call(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.CheckedTaskSupplier.class),
                org.mockito.ArgumentMatchers.any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = (LlmCallQueueService.CheckedTaskSupplier<String>) inv.getArgument(4);
            @SuppressWarnings("unchecked")
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = (LlmCallQueueService.ResultMetricsExtractor<String>) inv.getArgument(5);
            String raw = supplier.get(mock(LlmCallQueueService.TaskHandle.class));
            extractor.extract(raw);
            return raw;
        });

        when(llmCallQueueService.parseOpenAiUsageFromJson(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(2, 1, 3, 1))
                .thenReturn(new LlmCallQueueService.UsageMetrics(2, 1, 3, 1))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, null, 9, 1))
                .thenReturn(new LlmCallQueueService.UsageMetrics(null, 5, 9, 1));

        try (org.mockito.MockedConstruction<OpenAiCompatClient> mocked = org.mockito.Mockito.mockConstruction(
                OpenAiCompatClient.class,
                (m, c) -> when(m.chatCompletionsOnce(org.mockito.ArgumentMatchers.any()))
                        .thenReturn((String) null)
                        .thenReturn("{\"choices\":[{\"message\":{\"content\":\"tail\"}}]}")
        )) {
            Object r1 = callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.1, null, null, null, null, null, Map.of(), 1, null, Map.of()
            );
            Object r2 = callInstance(
                    gateway,
                    "callChatOnceSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Integer.class, List.class, Boolean.class, Integer.class, Map.class,
                            int.class, AtomicReference.class, Map.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, null, null, null, null, Map.of(), 1, null, Map.of()
            );
            assertNotNull(r1);
            assertNotNull(r2);
            assertTrue(mocked.constructed().size() >= 1);
        }
    }

    @Test
    void lambda_callChatOnceSingle_2_should_cover_null_usage_with_tokenizer_override() throws Exception {
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
                .thenReturn(null);
        when(tokenCountService.decideChatTokens(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(new TokenCountService.TokenDecision(11, 7, 18, null, "mock"));

        @SuppressWarnings("unchecked")
        LlmCallQueueService.UsageMetrics usage = (LlmCallQueueService.UsageMetrics) callInstance(
                gateway,
                "lambda$callChatOnceSingle$2",
                new Class[]{List.class, boolean.class, LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, Boolean.class, String.class},
                List.of(ChatMessage.user("hi")),
                false,
                LlmQueueTaskType.MODERATION_CHUNK,
                provider,
                "m1",
                Boolean.TRUE,
                "{\"choices\":[{\"message\":{\"content\":\"xyz\"}}]}"
        );
        assertNotNull(usage);
        assertEquals(11, usage.promptTokens());
        assertEquals(7, usage.completionTokens());
        assertEquals(18, usage.totalTokens());
        assertTrue(usage.estimatedCompletionTokens() != null && usage.estimatedCompletionTokens() >= 0);
    }

    @Test
    void extractStreamChunkStats_should_cover_choices_and_reasoning_guard_branches() throws Exception {
        LlmGateway gateway = gateway();
        AtomicReference<LlmCallQueueService.UsageMetrics> ref = new AtomicReference<>();
        long[] outChars = new long[]{0L};

        callInstance(
                gateway,
                "extractStreamChunkStats",
                new Class[]{String.class, boolean.class, long[].class, AtomicReference.class},
                "{\"usage\":{},\"choices\":null}",
                true,
                outChars,
                ref
        );
        assertEquals(0L, outChars[0]);

        callInstance(
                gateway,
                "extractStreamChunkStats",
                new Class[]{String.class, boolean.class, long[].class, AtomicReference.class},
                "{\"usage\":{},\"choices\":[{\"delta\":{\"reasoning_content\":\"\",\"content\":\"q\"},\"text\":\"\"}]}",
                true,
                outChars,
                ref
        );
        assertEquals(1L, outChars[0]);

        callInstance(
                gateway,
                "extractStreamChunkStats",
                new Class[]{String.class, boolean.class, long[].class, AtomicReference.class},
                "{\"usage\":{},\"choices\":[{\"delta\":null,\"text\":\"z\"}]}",
                true,
                outChars,
                ref
        );
        assertEquals(2L, outChars[0]);
    }

    @Test
    void normalize_and_directive_helpers_should_cover_remaining_edge_paths() throws Exception {
        LlmCallQueueService.UsageMetrics m1 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                10, 1, 5
        );
        assertEquals(10, m1.promptTokens());
        assertEquals(5, m1.completionTokens());
        assertEquals(15, m1.totalTokens());

        LlmCallQueueService.UsageMetrics m2 = (LlmCallQueueService.UsageMetrics) callStatic(
                "normalizeOpenAiCompatUsage",
                new Class[]{Integer.class, Integer.class, Integer.class},
                null, 5, 3
        );
        assertEquals(null, m2.promptTokens());
        assertEquals(5, m2.completionTokens());
        assertEquals(3, m2.totalTokens());

        assertFalse((boolean) callStatic("shouldSendDashscopeThinking", new Class[]{AiProvidersConfigService.ResolvedProvider.class}, (Object) null));
        AiProvidersConfigService.ResolvedProvider blank = new AiProvidersConfigService.ResolvedProvider(
                "p1", "OPENAI_COMPAT", " ", "k", "m", "e", Map.of(), Map.of(), 1000, 1000
        );
        assertFalse((boolean) callStatic("shouldSendDashscopeThinking", new Class[]{AiProvidersConfigService.ResolvedProvider.class}, blank));
        AiProvidersConfigService.ResolvedProvider intl = new AiProvidersConfigService.ResolvedProvider(
                "p2", "OPENAI_COMPAT", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", "k", "m", "e", Map.of(), Map.of(), 1000, 1000
        );
        assertTrue((boolean) callStatic("shouldSendDashscopeThinking", new Class[]{AiProvidersConfigService.ResolvedProvider.class}, intl));

        @SuppressWarnings("unchecked")
        List<ChatMessage> unchanged = (List<ChatMessage>) callStatic(
                "applyThinkingDirectiveToMessages",
                new Class[]{List.class, Boolean.class, String.class},
                List.of(new ChatMessage("user", List.of(Map.of("type", "text", "text", "x")))),
                Boolean.TRUE,
                "qwen3-32b"
        );
        assertEquals(1, unchanged.size());
        assertTrue(unchanged.get(0).content() instanceof List<?>);
    }

    @Test
    void removeLabelMapFromEmbeddedJson_should_cover_prefix_blank_and_invalid_suffix() throws Exception {
        LlmGateway gateway = gateway();
        String pureObj = "\n\n{\"label_taxonomy\":{\"label_map\":{\"x\":1},\"keep\":9},\"v\":1}";
        String outObj = (String) callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, pureObj);
        assertTrue(outObj.contains("\"keep\":9"));
        assertFalse(outObj.contains("label_map"));

        String oneNewline = "prefix\n{\"label_taxonomy\":{\"label_map\":{\"x\":1},\"k\":1}}";
        String out = (String) callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, oneNewline);
        assertTrue(out.contains("prefix"));
        assertFalse(out.contains("label_map"));

        String arrOnly = "\n[{\"label_taxonomy\":{\"label_map\":{\"x\":1},\"k\":1}}]";
        String outArrOnly = (String) callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, arrOnly);
        assertFalse(outArrOnly.contains("label_map"));

        String invalidJsonAfterPrefix = "prefix\n\n{bad-json";
        assertEquals(invalidJsonAfterPrefix, callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, invalidJsonAfterPrefix));

        String blankJsonPart = "prefix\n\n";
        assertEquals(blankJsonPart, callInstance(gateway, "removeLabelMapFromEmbeddedJson", new Class[]{String.class}, blankJsonPart));
    }

    @Test
    void lambda_callChatOnceSingle_2_should_cover_usage_fallback_and_tokenizer_paths() throws Exception {
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
                .thenReturn(null)
                .thenReturn(new LlmCallQueueService.UsageMetrics(3, null, null, null))
                .thenReturn(new LlmCallQueueService.UsageMetrics(2, 2, 4, 2))
                .thenReturn(new LlmCallQueueService.UsageMetrics(2, 2, 4, 2));
        when(tokenCountService.decideChatTokens(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(
                new TokenCountService.TokenDecision(9, 8, 17, null, "mock"),
                (TokenCountService.TokenDecision) null
        );

        @SuppressWarnings("unchecked")
        List<ChatMessage> patched = List.of(ChatMessage.user("hi"));
        Object u1 = callInstance(
                gateway,
                "lambda$callChatOnceSingle$2",
                new Class[]{List.class, boolean.class, LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, Boolean.class, String.class},
                patched, false, LlmQueueTaskType.TEXT_CHAT, provider, "m1", Boolean.TRUE, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"
        );
        assertNotNull(u1);

        Object u2 = callInstance(
                gateway,
                "lambda$callChatOnceSingle$2",
                new Class[]{List.class, boolean.class, LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, Boolean.class, String.class},
                patched, true, LlmQueueTaskType.TEXT_CHAT, provider, "m1", Boolean.FALSE, "{\"choices\":[{\"message\":{\"content\":\"<think>x</think>ok\"}}]}"
        );
        assertNotNull(u2);

        Object u3 = callInstance(
                gateway,
                "lambda$callChatOnceSingle$2",
                new Class[]{List.class, boolean.class, LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, Boolean.class, String.class},
                patched, false, LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", Boolean.TRUE, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"
        );
        assertNotNull(u3);

        Object u4 = callInstance(
                gateway,
                "lambda$callChatOnceSingle$2",
                new Class[]{List.class, boolean.class, LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, Boolean.class, String.class},
                patched, false, LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", Boolean.TRUE, "{\"choices\":[{\"message\":{\"content\":\"ok2\"}}]}"
        );
        assertNotNull(u4);
    }

    @Test
    void pickFallbackProviderId_should_skip_invalid_and_blank_models() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        AiProvidersConfigService.ResolvedProvider pBlank = new AiProvidersConfigService.ResolvedProvider(
                "p-blank", "OPENAI_COMPAT", "http://example.invalid", "k", " ", "e1", Map.of(), Map.of(), 1000, 1000
        );
        AiProvidersConfigService.ResolvedProvider pOk = new AiProvidersConfigService.ResolvedProvider(
                "p-ok", "OPENAI_COMPAT", "http://example.invalid", "k", "m-ok", "e1", Map.of(), Map.of(), 1000, 1000
        );
        when(aiProvidersConfigService.listEnabledProviderIds()).thenReturn(List.of("bad", "p-blank", "p-ok"));
        when(aiProvidersConfigService.resolveProvider("bad")).thenThrow(new IllegalStateException("bad"));
        when(aiProvidersConfigService.resolveProvider("p-blank")).thenReturn(pBlank);
        when(aiProvidersConfigService.resolveProvider("p-ok")).thenReturn(pOk);

        LlmGateway gateway = new LlmGateway(
                aiProvidersConfigService,
                mock(AiEmbeddingService.class),
                mock(AiRerankService.class),
                mock(LlmCallQueueService.class),
                mock(LlmRoutingService.class),
                mock(LlmRoutingTelemetryService.class),
                mock(TokenCountService.class)
        );

        String picked = (String) callInstance(
                gateway,
                "pickFallbackProviderId",
                new Class[]{java.util.function.Function.class},
                (java.util.function.Function<AiProvidersConfigService.ResolvedProvider, String>) AiProvidersConfigService.ResolvedProvider::defaultChatModel
        );
        assertEquals("p-ok", picked);
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
        OpenAiCompatClient.SseLineConsumer sink = line -> {};
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
        OpenAiCompatClient.SseLineConsumer sink = line -> {};

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

        callInstance(
                gateway,
                "lambda$callChatOnceSingle$1",
                new Class[]{
                        AtomicReference.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class, Double.class,
                        Boolean.class, Integer.class, int.class, OpenAiCompatClient.class, OpenAiCompatClient.ChatRequest.class,
                        boolean.class, LlmCallQueueService.TaskHandle.class
                },
                idOut, provider, "m1", List.of(ChatMessage.user("hi")), 0.2,
                Boolean.TRUE, null, 1, client, req, false, task
        );
        callInstance(
                gateway,
                "lambda$callChatOnceSingle$1",
                new Class[]{
                        AtomicReference.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class, Double.class,
                        Boolean.class, Integer.class, int.class, OpenAiCompatClient.class, OpenAiCompatClient.ChatRequest.class,
                        boolean.class, LlmCallQueueService.TaskHandle.class
                },
                idOut, provider, "m1", List.of(ChatMessage.user("hi")), 0.2,
                Boolean.FALSE, null, 1, client, req, true, task
        );
        callInstance(
                gateway,
                "lambda$callChatOnceSingle$1",
                new Class[]{
                        AtomicReference.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class, Double.class,
                        Boolean.class, Integer.class, int.class, OpenAiCompatClient.class, OpenAiCompatClient.ChatRequest.class,
                        boolean.class, LlmCallQueueService.TaskHandle.class
                },
                idOut, provider, "m1", List.of(ChatMessage.user("hi")), 0.2,
                null, null, 1, client, req, false, task
        );

        assertEquals("task-l1", idOut.get());
        assertTrue(outputs.stream().anyMatch(s -> s.contains("输出文本:")));
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
        OpenAiCompatClient.SseLineConsumer sink = line -> {};

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
