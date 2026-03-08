package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class TokenCountServiceTest {

    @Test
    void normalizeOutputText_nonThinking_should_stripThink_trim_and_normalize_ok_like_tokenText() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        TokenCountService svc = new TokenCountService(tokenize);

        TokenCountService.NormalizedOutput norm = svc.normalizeOutputText("<think>\n\n</think>\n\nok。", false);
        assertNotNull(norm);
        assertTrue(norm.strippedThink());
        assertEquals("ok。", norm.displayText());
        assertEquals("ok", norm.tokenText());
    }

    @Test
    void decideChatTokens_nvidia_qwen3_should_prefer_tokenizer_and_ignore_usage_completion() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doAnswer(inv -> {
            var req = inv.getArgument(0, com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest.class);
            var resp = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse();
            var usage = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse.Usage();
            if (req.getText() != null && req.getText().equals("ok")) {
                usage.setInputTokens(1);
            } else {
                usage.setInputTokens(0);
            }
            resp.setUsage(usage);
            return resp;
        }).when(tokenize).tokenize(any());

        TokenCountService svc = new TokenCountService(tokenize);
        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                "nvidia",
                "qwen/qwen3-next-80b-a3b-instruct",
                false,
                new LlmCallQueueService.UsageMetrics(37, 2, 39, 1),
                List.of(new ChatMessage("user", "hi")),
                "\n\nok"
        );

        assertNotNull(dec);
        assertEquals(1, dec.tokensOut());
        assertEquals("TOKENIZER", dec.tokensOutSource());
        assertEquals("ok", dec.normalizedOutput().displayText());
        assertTrue(dec.normalizedOutput().strippedWhitespace());
    }

    @Test
    void decideChatTokens_llmStudio_qwen3_with_thinkBlocks_should_use_tokenizer_and_count_ok_as_one() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doAnswer(inv -> {
            var req = inv.getArgument(0, com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest.class);
            var resp = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse();
            var usage = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse.Usage();
            if (req.getText() != null && req.getText().equals("ok")) {
                usage.setInputTokens(1);
            } else {
                usage.setInputTokens(0);
            }
            resp.setUsage(usage);
            return resp;
        }).when(tokenize).tokenize(any());

        TokenCountService svc = new TokenCountService(tokenize);
        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                "llm-studio",
                "qwen/qwen3-30b-a3b",
                false,
                new LlmCallQueueService.UsageMetrics(37, 7, 44, 2),
                List.of(new ChatMessage("user", "hi")),
                "<think>\n\n</think>\n\nok。"
        );

        assertNotNull(dec);
        assertEquals(1, dec.tokensOut());
        assertEquals("TOKENIZER", dec.tokensOutSource());
        assertEquals("ok。", dec.normalizedOutput().displayText());
        assertTrue(dec.normalizedOutput().strippedThink());
    }

    @Test
    void decideChatTokens_nvidia_when_tokenizer_fails_should_use_estimated_completion_tokens() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doThrow(new RuntimeException("tokenizer down")).when(tokenize).tokenize(any());

        TokenCountService svc = new TokenCountService(tokenize);
        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                "nvidia",
                "qwen/qwen3-235b-a22b",
                false,
                new LlmCallQueueService.UsageMetrics(38, 6, 44, 1),
                List.of(new ChatMessage("user", "hi")),
                "\n\nok"
        );

        assertNotNull(dec);
        assertEquals(1, dec.tokensOut());
        assertEquals("ESTIMATED", dec.tokensOutSource());
        assertEquals("ok", dec.normalizedOutput().displayText());
        assertTrue(dec.normalizedOutput().strippedWhitespace());
    }

    @Test
    void decideChatTokens_nvidia_when_tokenizer_fails_and_no_estimated_should_fallback_ok_to_one_token() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doThrow(new RuntimeException("tokenizer down")).when(tokenize).tokenize(any());

        TokenCountService svc = new TokenCountService(tokenize);
        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                "nvidia",
                "qwen/qwen3-next-80b-a3b-instruct",
                false,
                new LlmCallQueueService.UsageMetrics(37, 2, 39, null),
                List.of(new ChatMessage("user", "hi")),
                "ok"
        );

        assertNotNull(dec);
        assertEquals(1, dec.tokensOut());
        assertEquals("HEURISTIC_OK", dec.tokensOutSource());
    }

    @Test
    void decideChatTokens_preferTokenizerIn_should_override_usage_promptTokens_when_available() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doAnswer(inv -> {
            var req = inv.getArgument(0, com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest.class);
            var resp = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse();
            var usage = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse.Usage();
            if (req.getMessages() != null && !req.getMessages().isEmpty()) {
                usage.setInputTokens(123);
            } else if (req.getText() != null && req.getText().equals("ok")) {
                usage.setInputTokens(1);
            } else {
                usage.setInputTokens(0);
            }
            resp.setUsage(usage);
            return resp;
        }).when(tokenize).tokenize(any());

        TokenCountService svc = new TokenCountService(tokenize);
        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                "dashscope",
                "qwen-plus",
                false,
                new LlmCallQueueService.UsageMetrics(7, 0, 7, null),
                List.of(ChatMessage.system("sys"), new ChatMessage("user", "hi")),
                "ok",
                true
        );

        assertNotNull(dec);
        assertEquals(123, dec.tokensIn());
        assertEquals(1, dec.tokensOut());
    }

    @Test
    void normalizeOutputText_enableThinking_should_keep_raw_and_not_strip() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        TokenCountService svc = new TokenCountService(tokenize);

        String raw = "  <think>do not strip</think> reasoning_content ok。  ";
        TokenCountService.NormalizedOutput norm = svc.normalizeOutputText(raw, true);
        assertEquals(raw, norm.rawText());
        assertEquals(raw, norm.displayText());
        assertEquals(raw, norm.tokenText());
        assertTrue(!norm.strippedThink());
        assertTrue(!norm.strippedWhitespace());
    }

    @Test
    void normalizeOutputText_nonThinking_should_strip_reasoning_markers() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        TokenCountService svc = new TokenCountService(tokenize);

        String raw = " <reasoning_content>A</reasoning_content> &lt;reasoning_content&gt;B&lt;/reasoning_content&gt; ";
        TokenCountService.NormalizedOutput norm = svc.normalizeOutputText(raw, false);
        assertEquals("<>A</> &lt;&gt;B&lt;/&gt;", norm.displayText());
        assertEquals("<>A</> &lt;&gt;B&lt;/&gt;", norm.tokenText());
        assertTrue(norm.strippedThink());
        assertTrue(norm.strippedWhitespace());
    }

    @Test
    void countTextTokens_should_handle_blank_null_response_and_usage_null() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doAnswer(inv -> {
            var req = inv.getArgument(0, com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest.class);
            if ("resp-null".equals(req.getText())) return null;
            if ("usage-null".equals(req.getText())) return new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse();
            var resp = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse();
            var usage = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse.Usage();
            usage.setInputTokens(9);
            resp.setUsage(usage);
            return resp;
        }).when(tokenize).tokenize(any());
        TokenCountService svc = new TokenCountService(tokenize);

        assertNull(svc.countTextTokens(null));
        assertNull(svc.countTextTokens("   "));
        assertEquals(9, svc.countTextTokens(" ok "));
        assertNull(svc.countTextTokens("resp-null"));
        assertNull(svc.countTextTokens("usage-null"));
    }

    @Test
    void countTextTokens_should_return_null_when_tokenizer_throws_exception() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doThrow(new RuntimeException("tokenizer failed")).when(tokenize).tokenize(any());
        TokenCountService svc = new TokenCountService(tokenize);

        assertNull(svc.countTextTokens("hello"));
    }

    @Test
    void countChatMessagesTokens_should_cover_message_content_shapes_and_exception_path() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doAnswer(inv -> {
            var req = inv.getArgument(0, com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest.class);
            if (req.getMessages() != null && req.getMessages().size() == 3) {
                var first = req.getMessages().get(0);
                var second = req.getMessages().get(1);
                var third = req.getMessages().get(2);
                assertEquals("user", first.getRole());
                assertEquals("plain", first.getContent());
                assertEquals("T\nhttps://img\n42", second.getContent());
                assertEquals("77", third.getContent());
                var resp = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse();
                var usage = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse.Usage();
                usage.setInputTokens(33);
                resp.setUsage(usage);
                return resp;
            }
            throw new RuntimeException("boom");
        }).when(tokenize).tokenize(any());

        TokenCountService svc = new TokenCountService(tokenize);
        assertNull(svc.countChatMessagesTokens(null));
        assertNull(svc.countChatMessagesTokens(List.of()));
        assertNull(svc.countChatMessagesTokens(Arrays.asList(new ChatMessage(null, "x"), new ChatMessage(" ", "x"), null)));

        Integer tokens = svc.countChatMessagesTokens(Arrays.asList(
                null,
                new ChatMessage("", "skip role"),
                new ChatMessage("user", "plain"),
                new ChatMessage("assistant", Arrays.asList(
                        null,
                        Map.of("text", "T"),
                        Map.of("image_url", Map.of("url", "https://img")),
                        42
                )),
                new ChatMessage("system", 77),
                new ChatMessage("tool", null)
        ));
        assertNull(tokens);

        assertNull(svc.countChatMessagesTokens(List.of(new ChatMessage("user", "force exception"))));
    }

    @Test
    void countChatMessagesTokens_should_cover_resp_null_and_usage_null_branches() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doAnswer(inv -> {
            var req = inv.getArgument(0, com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest.class);
            String first = req.getMessages() == null || req.getMessages().isEmpty() ? "" : req.getMessages().get(0).getContent();
            if ("resp-null".equals(first)) {
                return null;
            }
            if ("usage-null".equals(first)) {
                return new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse();
            }
            var resp = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse();
            var usage = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse.Usage();
            usage.setInputTokens(8);
            resp.setUsage(usage);
            return resp;
        }).when(tokenize).tokenize(any());
        TokenCountService svc = new TokenCountService(tokenize);

        assertNull(svc.countChatMessagesTokens(List.of(new ChatMessage("user", "resp-null"))));
        assertNull(svc.countChatMessagesTokens(List.of(new ChatMessage("user", "usage-null"))));
        assertEquals(8, svc.countChatMessagesTokens(List.of(new ChatMessage("user", "ok"))));
    }

    @Test
    void decideChatTokens_when_not_forced_should_use_usage_completion_and_usage_total() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doThrow(new RuntimeException("tokenizer down")).when(tokenize).tokenize(any());
        TokenCountService svc = new TokenCountService(tokenize);

        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                "dashscope",
                "qwen-plus",
                false,
                new LlmCallQueueService.UsageMetrics(null, 6, 66, 6),
                List.of(new ChatMessage("user", "hello")),
                "hello"
        );
        assertEquals(6, dec.tokensOut());
        assertEquals("USAGE", dec.tokensOutSource());
        assertEquals(66, dec.totalTokens());
        assertNull(dec.tokensIn());
    }

    @Test
    void decideChatTokens_when_forced_and_completion_exists_should_fallback_usage_completion() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doThrow(new RuntimeException("tokenizer down")).when(tokenize).tokenize(any());
        TokenCountService svc = new TokenCountService(tokenize);

        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                "dashscope",
                "qwen-plus",
                false,
                new LlmCallQueueService.UsageMetrics(12, 7, 19, 1),
                List.of(new ChatMessage("user", "hi")),
                "hello"
        );
        assertEquals(1, dec.tokensOut());
        assertEquals("ESTIMATED", dec.tokensOutSource());
        assertEquals(13, dec.totalTokens());
    }

    @Test
    void decideChatTokens_when_total_less_than_prompt_and_out_missing_should_use_usage_total_as_out() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doAnswer(inv -> {
            var req = inv.getArgument(0, com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest.class);
            if (req.getText() != null) throw new RuntimeException("tokenizer text failed");
            var resp = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse();
            var usage = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse.Usage();
            usage.setInputTokens(50);
            resp.setUsage(usage);
            return resp;
        }).when(tokenize).tokenize(any());
        TokenCountService svc = new TokenCountService(tokenize);

        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                "dashscope",
                "qwen-plus",
                true,
                new LlmCallQueueService.UsageMetrics(null, 0, 10, 0),
                List.of(new ChatMessage("user", "x")),
                "x"
        );
        assertEquals(50, dec.tokensIn());
        assertEquals(10, dec.tokensOut());
        assertEquals("USAGE_TOTAL_AS_OUT", dec.tokensOutSource());
        assertEquals(60, dec.totalTokens());
    }

    @Test
    void decideChatTokens_should_cover_enableThinking_true_and_total_fallback_paths() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doThrow(new RuntimeException("tokenizer down")).when(tokenize).tokenize(any());
        TokenCountService svc = new TokenCountService(tokenize);

        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                "dashscope",
                "qwen-plus",
                true,
                new LlmCallQueueService.UsageMetrics(3, null, 11, 5),
                List.of(new ChatMessage("user", "x")),
                "  ok  "
        );
        assertEquals(3, dec.tokensIn());
        assertEquals(5, dec.tokensOut());
        assertEquals("ESTIMATED", dec.tokensOutSource());
        assertEquals(8, dec.totalTokens());
        assertFalse(dec.normalizedOutput().strippedThink());
        assertFalse(dec.normalizedOutput().strippedWhitespace());
    }

    @Test
    void decideChatTokens_should_keep_tokenizer_out_when_usage_total_less_than_prompt() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doAnswer(inv -> {
            var req = inv.getArgument(0, com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest.class);
            if (req.getText() != null) {
                var resp = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse();
                var usage = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse.Usage();
                usage.setInputTokens(2);
                resp.setUsage(usage);
                return resp;
            }
            var resp = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse();
            var usage = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse.Usage();
            usage.setInputTokens(50);
            resp.setUsage(usage);
            return resp;
        }).when(tokenize).tokenize(any());
        TokenCountService svc = new TokenCountService(tokenize);

        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                "dashscope",
                "qwen-plus",
                true,
                new LlmCallQueueService.UsageMetrics(null, 0, 10, 0),
                List.of(new ChatMessage("user", "hi")),
                "hello"
        );
        assertEquals(50, dec.tokensIn());
        assertEquals(2, dec.tokensOut());
        assertEquals("TOKENIZER", dec.tokensOutSource());
        assertEquals(52, dec.totalTokens());
    }

    @Test
    void decideChatTokens_preferTokenizerIn_true_when_in_null_should_keep_usage_prompt() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doAnswer(inv -> {
            var req = inv.getArgument(0, com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest.class);
            if (req.getMessages() != null) {
                return null;
            }
            var resp = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse();
            var usage = new com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse.Usage();
            usage.setInputTokens(3);
            resp.setUsage(usage);
            return resp;
        }).when(tokenize).tokenize(any());
        TokenCountService svc = new TokenCountService(tokenize);

        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                "dashscope",
                "qwen-plus",
                true,
                new LlmCallQueueService.UsageMetrics(9, 0, 9, 0),
                List.of(new ChatMessage("user", "hi")),
                "hello",
                true
        );
        assertEquals(9, dec.tokensIn());
        assertEquals(3, dec.tokensOut());
        assertEquals("TOKENIZER", dec.tokensOutSource());
    }

    @Test
    void decideChatTokens_when_not_forced_and_completion_non_positive_should_keep_out_null() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doThrow(new RuntimeException("tokenizer down")).when(tokenize).tokenize(any());
        TokenCountService svc = new TokenCountService(tokenize);

        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                "dashscope",
                "qwen-plus",
                false,
                new LlmCallQueueService.UsageMetrics(null, 0, 22, 0),
                List.of(new ChatMessage("user", "hello")),
                "hello"
        );
        assertNull(dec.tokensIn());
        assertNull(dec.tokensOut());
        assertNull(dec.tokensOutSource());
        assertEquals(22, dec.totalTokens());
    }

    @Test
    void decideChatTokens_when_usage_null_and_tokenizers_fail_should_keep_totals_null() {
        OpenSearchTokenizeService tokenize = mock(OpenSearchTokenizeService.class);
        doThrow(new RuntimeException("tokenizer down")).when(tokenize).tokenize(any());
        TokenCountService svc = new TokenCountService(tokenize);

        TokenCountService.TokenDecision dec = svc.decideChatTokens(
                null,
                null,
                false,
                null,
                List.of(new ChatMessage("user", "x")),
                ""
        );
        assertNull(dec.tokensIn());
        assertNull(dec.tokensOut());
        assertNull(dec.totalTokens());
        assertNull(dec.tokensOutSource());
    }

    @Test
    void private_helpers_should_cover_remaining_branches_via_reflection() throws Exception {
        assertTrue((Boolean) invokeStatic("isNvidiaProvider", new Class[]{String.class}, "nvidia"));
        assertTrue((Boolean) invokeStatic("isNvidiaProvider", new Class[]{String.class}, " NVIDIA-foo "));
        assertFalse((Boolean) invokeStatic("isNvidiaProvider", new Class[]{String.class}, "dashscope"));

        assertTrue((Boolean) invokeStatic("isQwen3Model", new Class[]{String.class}, "qwen/qwen3-next"));
        assertTrue((Boolean) invokeStatic("isQwen3Model", new Class[]{String.class}, "x/qwen3-32b"));
        assertTrue((Boolean) invokeStatic("isQwen3Model", new Class[]{String.class}, "abc-qwen3-30b"));
        assertFalse((Boolean) invokeStatic("isQwen3Model", new Class[]{String.class}, "gpt-4o"));

        assertEquals("x", invokeStatic("stripThinkBlocks", new Class[]{String.class}, "<think>a</think>x"));
        assertEquals(" ", invokeStatic("stripThinkBlocks", new Class[]{String.class}, " "));
        assertNull(invokeStatic("stripThinkBlocks", new Class[]{String.class}, (Object) null));

        assertEquals(-1, invokeStatic("indexOfIgnoreCase",
                new Class[]{String.class, String.class, int.class}, null, "a", 0));
        assertEquals(-1, invokeStatic("indexOfIgnoreCase",
                new Class[]{String.class, String.class, int.class}, "abc", null, 0));
        assertEquals(2, invokeStatic("indexOfIgnoreCase",
                new Class[]{String.class, String.class, int.class}, "abCDe", "cd", 0));
        assertEquals(3, invokeStatic("indexOfIgnoreCase",
                new Class[]{String.class, String.class, int.class}, "abc", "", 3));
        assertEquals(-1, invokeStatic("indexOfIgnoreCase",
                new Class[]{String.class, String.class, int.class}, "abc", "", 4));
        assertEquals(-1, invokeStatic("indexOfIgnoreCase",
                new Class[]{String.class, String.class, int.class}, "ab", "abcd", 0));
        assertEquals(1, invokeStatic("indexOfIgnoreCase",
                new Class[]{String.class, String.class, int.class}, "Abc", "b", -3));

        assertEquals("x", invokeStatic("removeMarkerWordIgnoreCase",
                new Class[]{String.class, String.class}, "x", ""));
        assertEquals(" ", invokeStatic("removeMarkerWordIgnoreCase",
                new Class[]{String.class, String.class}, " ", "x"));
        assertEquals("x", invokeStatic("removeMarkerWordIgnoreCase",
                new Class[]{String.class, String.class}, "x", "zzz"));
        assertEquals("A--B--", invokeStatic("removeMarkerWordIgnoreCase",
                new Class[]{String.class, String.class}, "Axx--Bxx--", "xX"));

        assertNull(invokeStatic("stripReasoningArtifacts", new Class[]{String.class}, (Object) null));
        assertEquals(" ", invokeStatic("stripReasoningArtifacts", new Class[]{String.class}, " "));
        assertEquals("<>abc</>", invokeStatic("stripReasoningArtifacts",
                new Class[]{String.class}, "<reasoning_content>abc</reasoning_content>"));
    }

    private static Object invokeStatic(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = TokenCountService.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }
}
