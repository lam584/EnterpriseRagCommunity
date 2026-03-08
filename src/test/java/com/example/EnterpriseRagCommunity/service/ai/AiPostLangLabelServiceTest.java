package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostLangLabelSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostLangLabelSuggestResponse;
import com.example.EnterpriseRagCommunity.entity.ai.PostLangLabelGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiPostLangLabelServiceTest {

    @Test
    void suggestLanguages_should_throw_when_disabled() {
        Fixture f = new Fixture();
        f.config.setEnabled(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.service.suggestLanguages(new AiPostLangLabelSuggestRequest()));
        assertTrue(ex.getMessage().contains("语言标签生成已关闭"));
        verify(f.llmGateway, never()).chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any());
    }

    @Test
    void suggestLanguages_should_throw_when_prompt_missing() {
        Fixture f = new Fixture();
        f.config.setPromptCode("NOT_FOUND");
        when(f.promptsRepository.findByPromptCode("NOT_FOUND")).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.service.suggestLanguages(new AiPostLangLabelSuggestRequest()));
        assertTrue(ex.getMessage().contains("Prompt code not found"));
    }

    @Test
    void suggestLanguages_should_fallback_default_prompt_code_and_parse_message_content() {
        Fixture f = new Fixture();
        f.config.setPromptCode("   ");
        f.config.setMaxContentChars(5);
        f.prompt.setUserPromptTemplate("T={{title}};C={{content}}");
        f.mockLlm("{\"choices\":[{\"message\":{\"content\":\"{\\\"languages\\\":[\\\"EN\\\",\\\" en \\\",\\\"\\\\\\\"ZH\\\\\\\"\\\",\\\"ja\\\",\\\"ko\\\",123]}\"}}]}", "used-model");

        AiPostLangLabelSuggestRequest req = new AiPostLangLabelSuggestRequest();
        req.setTitle("  Hello  ");
        req.setContent("  abcdefg  ");
        AiPostLangLabelSuggestResponse resp = f.service.suggestLanguages(req);

        assertEquals(List.of("en", "zh", "ja"), resp.getLanguages());
        assertEquals("used-model", resp.getModel());
        assertNotNull(resp.getLatencyMs());
        assertTrue(resp.getLatencyMs() >= 0L);
        verify(f.promptsRepository).findByPromptCode(PostLangLabelGenConfigService.DEFAULT_PROMPT_CODE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.LANGUAGE_TAG_GEN),
                eq("provider-1"),
                eq("model-from-resolver"),
                msgCap.capture(),
                eq(0.15),
                eq(0.35),
                isNull(),
                isNull(),
                eq(Boolean.TRUE)
        );
        String joined = msgCap.getValue().toString();
        assertTrue(joined.contains("T=Hello"));
        assertTrue(joined.contains("C=abcde"));
        assertFalse(joined.contains("abcdefg"));
    }

    @Test
    void suggestLanguages_should_fallback_to_choices_text_when_message_content_missing() {
        Fixture f = new Fixture();
        f.mockLlm("{\"choices\":[{\"text\":\"{\\\"languages\\\":[\\\" zh-CN \\\",\\\"en\\\"]}\"}]}", "m2");

        AiPostLangLabelSuggestRequest req = new AiPostLangLabelSuggestRequest();
        req.setTitle("t");
        req.setContent("c");
        AiPostLangLabelSuggestResponse resp = f.service.suggestLanguages(req);

        assertEquals(List.of("zh-cn", "en"), resp.getLanguages());
        assertEquals("m2", resp.getModel());
    }

    @Test
    void suggestLanguages_should_throw_wrapped_when_llm_gateway_failed() {
        Fixture f = new Fixture();
        RuntimeException cause = new RuntimeException("boom");
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any())).thenThrow(cause);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.service.suggestLanguages(new AiPostLangLabelSuggestRequest()));
        assertTrue(ex.getMessage().contains("上游AI调用失败"));
        assertSame(cause, ex.getCause());
    }

    @Test
    void suggestLanguages_should_throw_when_routed_null_and_raw_unparseable() {
        Fixture f = new Fixture();
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult("not-json", "p", "m", null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> f.service.suggestLanguages(new AiPostLangLabelSuggestRequest()));
        assertTrue(ex.getMessage().contains("AI 输出无法解析为语言标签"));
        assertNotNull(ex.getCause());
    }

    @Test
    void suggestLanguages_should_use_null_model_and_no_truncate_when_non_positive_max_chars() {
        Fixture f = new Fixture();
        f.config.setMaxContentChars(0);
        when(f.promptLlmParamResolver.resolveText(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PromptLlmParams("provider-2", null, 0.0, 0.2, null, Boolean.FALSE));
        f.mockLlm("xx {\"languages\":[\"zh\",\"en\"]} yy", "returned-model");

        AiPostLangLabelSuggestRequest req = new AiPostLangLabelSuggestRequest();
        req.setTitle(null);
        req.setContent("  abcdefghijklmnopqrstuvwxyz  ");
        AiPostLangLabelSuggestResponse resp = f.service.suggestLanguages(req);
        assertEquals(List.of("zh", "en"), resp.getLanguages());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.LANGUAGE_TAG_GEN),
                eq("provider-2"),
                isNull(),
                msgCap.capture(),
                eq(0.0),
                eq(0.2),
                isNull(),
                isNull(),
                eq(Boolean.FALSE)
        );
        assertTrue(msgCap.getValue().toString().contains("abcdefghijklmnopqrstuvwxyz"));
    }

    @Test
    void suggestLanguages_should_use_default_max_chars_when_config_missing_and_return_empty_languages() {
        Fixture f = new Fixture();
        f.config.setMaxContentChars(null);
        f.config.setPromptCode(null);
        f.prompt.setUserPromptTemplate("C={{content}}");
        String longContent = "x".repeat(PostLangLabelGenConfigService.DEFAULT_MAX_CONTENT_CHARS + 50);
        f.mockLlm("{\"choices\":[{}]}", "m-empty");

        AiPostLangLabelSuggestRequest req = new AiPostLangLabelSuggestRequest();
        req.setContent(longContent);
        AiPostLangLabelSuggestResponse resp = f.service.suggestLanguages(req);

        assertEquals(List.of(), resp.getLanguages());
        assertEquals("m-empty", resp.getModel());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.LANGUAGE_TAG_GEN),
                eq("provider-1"),
                eq("model-from-resolver"),
                msgCap.capture(),
                eq(0.15),
                eq(0.35),
                isNull(),
                isNull(),
                eq(Boolean.TRUE)
        );
        String rendered = msgCap.getValue().toString();
        assertTrue(rendered.contains("x".repeat(PostLangLabelGenConfigService.DEFAULT_MAX_CONTENT_CHARS)));
        assertFalse(rendered.contains("x".repeat(PostLangLabelGenConfigService.DEFAULT_MAX_CONTENT_CHARS + 1)));
    }

    @Test
    void suggestLanguages_should_throw_when_routed_result_is_null() {
        Fixture f = new Fixture();
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        AiPostLangLabelSuggestResponse resp = f.service.suggestLanguages(new AiPostLangLabelSuggestRequest());
        assertEquals(List.of(), resp.getLanguages());
    }

    @Test
    void private_helpers_should_cover_null_and_length_branches() throws Exception {
        assertEquals("", invokePrivateStatic(AiPostLangLabelService.class, "cleanLang", new Class[]{String.class}, new Object[]{null}));
        String cleaned = (String) invokePrivateStatic(
                AiPostLangLabelService.class,
                "cleanLang",
                new Class[]{String.class},
                new Object[]{"  \"ABCDEFGHIJKLMNOPQRST\"  "}
        );
        assertEquals("abcdefghijklmnop", cleaned);

        String rendered = (String) invokePrivateStatic(
                AiPostLangLabelService.class,
                "renderPrompt",
                new Class[]{String.class, String.class, String.class},
                new Object[]{null, null, null}
        );
        assertEquals("", rendered);
    }

    private static Object invokePrivateStatic(Class<?> clazz, String name, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method m = clazz.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static final class Fixture {
        private final PostLangLabelGenConfigService configService = mock(PostLangLabelGenConfigService.class);
        private final LlmGateway llmGateway = mock(LlmGateway.class);
        private final PromptsRepository promptsRepository = mock(PromptsRepository.class);
        private final PromptLlmParamResolver promptLlmParamResolver = mock(PromptLlmParamResolver.class);

        private final PostLangLabelGenConfigEntity config = new PostLangLabelGenConfigEntity();
        private final PromptsEntity prompt = new PromptsEntity();
        private final AiPostLangLabelService service;

        private Fixture() {
            config.setEnabled(true);
            config.setPromptCode("LANG_DETECT");
            config.setMaxContentChars(100);
            when(configService.getConfigEntityOrDefault()).thenReturn(config);

            prompt.setSystemPrompt("system");
            prompt.setUserPromptTemplate("TITLE={{title}}\nCONTENT={{content}}");
            when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt));

            when(promptLlmParamResolver.resolveText(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PromptLlmParams("provider-1", "model-from-resolver", 0.15, 0.35, null, Boolean.TRUE));

            service = new AiPostLangLabelService(configService, llmGateway, promptsRepository, promptLlmParamResolver);
        }

        private void mockLlm(String raw, String model) {
            when(llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
                    .thenReturn(new LlmGateway.RoutedChatOnceResult(raw, "provider-used", model, null));
        }
    }
}
