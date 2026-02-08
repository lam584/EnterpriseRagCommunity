package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
