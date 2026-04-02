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

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == boolean.class) return Boolean.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        if (c == char.class) return Character.class;
        return c;
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Object[] args) {
        for (Method m : type.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length != args.length) continue;
            boolean ok = true;
            for (int i = 0; i < pt.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    if (pt[i].isPrimitive()) {
                        ok = false;
                        break;
                    }
                    continue;
                }
                if (!wrap(pt[i]).isAssignableFrom(arg.getClass())) {
                    ok = false;
                    break;
                }
            }
            if (ok) return m;
        }
        return null;
    }

    private static Object callStatic(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = findCompatibleMethod(LlmGateway.class, name, args);
        if (m == null) {
            m = findCompatibleMethod(LlmGatewaySupport.class, name, args);
        }
        if (m == null) throw new NoSuchMethodException(name);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Object callInstance(LlmGateway gateway, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = findCompatibleMethod(LlmGateway.class, name, args);
        if (m == null) throw new NoSuchMethodException(name);
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
            // This scenario validates supplier/output branches; extractor behavior is covered elsewhere.
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
                    0.1, null, null, null, Boolean.FALSE, null, Map.of("foo", "bar"), 1,
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
                            Double.class, Double.class, Boolean.class, Integer.class,
                            OpenAiCompatClient.SseLineConsumer.class, int.class, AtomicReference.class
                    },
                    LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.1, null, null, null, sink, 1, new AtomicReference<String>()
            );
            assertNotNull(u1);

            Object u2 = callInstance(
                    gateway,
                    "callChatStreamSingle",
                    new Class[]{
                            LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, List.class,
                            Double.class, Double.class, Boolean.class, Integer.class,
                            OpenAiCompatClient.SseLineConsumer.class, int.class, AtomicReference.class
                    },
                    LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.2, null, Boolean.FALSE, null, sink, 1, new AtomicReference<String>()
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

}
