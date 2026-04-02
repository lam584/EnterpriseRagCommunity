package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class LlmGatewayPrivateHelpersCoveragePart2Test {

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

    private static Object callCallChatOnceSingleLambda(LlmGateway gateway, Class<?>[] paramTypes, Object... args) throws Exception {
        for (Method m : LlmGateway.class.getDeclaredMethods()) {
            if (!m.getName().startsWith("lambda$callChatOnceSingle$")) {
                continue;
            }
            Class<?>[] actual = m.getParameterTypes();
            boolean isStatic = java.lang.reflect.Modifier.isStatic(m.getModifiers());
            int shift = (isStatic && actual.length == paramTypes.length + 1 && actual[0] == LlmGateway.class) ? 1 : 0;
            if (actual.length != paramTypes.length + shift) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!actual[i + shift].equals(paramTypes[i])) {
                    match = false;
                    break;
                }
            }
            if (!match) {
                continue;
            }
            m.setAccessible(true);
            if (shift == 1) {
                Object[] invokeArgs = new Object[args.length + 1];
                invokeArgs[0] = gateway;
                System.arraycopy(args, 0, invokeArgs, 1, args.length);
                return m.invoke(null, invokeArgs);
            }
            return isStatic ? m.invoke(null, args) : m.invoke(gateway, args);
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
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = inv.getArgument(4);
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = inv.getArgument(5);
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
            assertFalse(mocked.constructed().isEmpty());
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
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = inv.getArgument(4);
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = inv.getArgument(5);
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
            assertFalse(mocked.constructed().isEmpty());
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
        assertNull(cleaned.get(0).content());
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
            LlmCallQueueService.CheckedTaskSupplier<LlmCallQueueService.UsageMetrics> supplier =
                    inv.getArgument(4);
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
                            Double.class, Double.class, Boolean.class, Integer.class,
                            OpenAiCompatClient.SseLineConsumer.class, int.class, AtomicReference.class
                    },
                    LlmQueueTaskType.TEXT_CHAT, provider, "m1", List.of(ChatMessage.user("hi")),
                    0.1, null, Boolean.TRUE, null, (OpenAiCompatClient.SseLineConsumer) line -> {
                    }, 1, null
            );
            assertNotNull(usage);
            assertFalse(mocked.constructed().isEmpty());
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
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = inv.getArgument(4);
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = inv.getArgument(5);
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
            assertFalse(mocked.constructed().isEmpty());
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
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = inv.getArgument(4);
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = inv.getArgument(5);
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
            assertFalse(mocked.constructed().isEmpty());
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
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = inv.getArgument(4);
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = inv.getArgument(5);
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
            assertFalse(mocked.constructed().isEmpty());
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
            LlmCallQueueService.CheckedTaskSupplier<String> supplier = inv.getArgument(4);
            LlmCallQueueService.ResultMetricsExtractor<String> extractor = inv.getArgument(5);
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
                        .thenReturn(null)
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
            assertFalse(mocked.constructed().isEmpty());
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

        LlmCallQueueService.UsageMetrics usage = (LlmCallQueueService.UsageMetrics) callCallChatOnceSingleLambda(
                gateway,
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
        assertNull(m2.promptTokens());
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
        assertInstanceOf(List.class, unchanged.getFirst().content());
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

        List<ChatMessage> patched = List.of(ChatMessage.user("hi"));
        Object u1 = callCallChatOnceSingleLambda(
                gateway,
                new Class[]{List.class, boolean.class, LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, Boolean.class, String.class},
                patched, false, LlmQueueTaskType.TEXT_CHAT, provider, "m1", Boolean.TRUE, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"
        );
        assertNotNull(u1);

        Object u2 = callCallChatOnceSingleLambda(
                gateway,
                new Class[]{List.class, boolean.class, LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, Boolean.class, String.class},
                patched, true, LlmQueueTaskType.TEXT_CHAT, provider, "m1", Boolean.FALSE, "{\"choices\":[{\"message\":{\"content\":\"<think>x</think>ok\"}}]}"
        );
        assertNotNull(u2);

        Object u3 = callCallChatOnceSingleLambda(
                gateway,
                new Class[]{List.class, boolean.class, LlmQueueTaskType.class, AiProvidersConfigService.ResolvedProvider.class, String.class, Boolean.class, String.class},
                patched, false, LlmQueueTaskType.MODERATION_CHUNK, provider, "m1", Boolean.TRUE, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"
        );
        assertNotNull(u3);

        Object u4 = callCallChatOnceSingleLambda(
                gateway,
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

}
