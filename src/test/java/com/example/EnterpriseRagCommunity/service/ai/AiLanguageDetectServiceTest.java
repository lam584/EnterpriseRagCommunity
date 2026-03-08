package com.example.EnterpriseRagCommunity.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateConfigEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;

class AiLanguageDetectServiceTest {
    @Test
    void detectLanguages_should_throw_when_disabled() {
        SemanticTranslateConfigService cfgService = mock(SemanticTranslateConfigService.class);
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(false);
        when(cfgService.getConfigEntityOrDefault()).thenReturn(cfg);

        AiLanguageDetectService s = new AiLanguageDetectService(
                cfgService,
                mock(LlmGateway.class),
                mock(PromptsRepository.class),
                mock(PromptLlmParamResolver.class)
        );
        assertThrows(IllegalStateException.class, () -> s.detectLanguages("hi"));
    }

    @Test
    void detectLanguages_should_throw_when_prompt_missing() {
        SemanticTranslateConfigService cfgService = mock(SemanticTranslateConfigService.class);
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(true);
        cfg.setMaxContentChars(1000);
        when(cfgService.getConfigEntityOrDefault()).thenReturn(cfg);

        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.empty());

        AiLanguageDetectService s = new AiLanguageDetectService(
                cfgService,
                mock(LlmGateway.class),
                promptsRepository,
                mock(PromptLlmParamResolver.class)
        );
        assertThrows(IllegalStateException.class, () -> s.detectLanguages("hi"));
    }

    @Test
    void detectLanguages_should_truncate_input_and_parse_from_choices_message_content() {
        SemanticTranslateConfigService cfgService = mock(SemanticTranslateConfigService.class);
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(true);
        cfg.setMaxContentChars(5);
        when(cfgService.getConfigEntityOrDefault()).thenReturn(cfg);

        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt("sys");
        p.setUserPromptTemplate("{{content}}");
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(p));

        PromptLlmParamResolver resolver = mock(PromptLlmParamResolver.class);
        when(resolver.resolveText(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any()))
                .thenReturn(new PromptLlmParams("pid", "m", 0.0, 8.0, null, false));

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatOnceRouted(any(), anyString(), anyString(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult("{\"choices\":[{\"message\":{\"content\":\"{\\\"languages\\\":[\\\"EN\\\",\\\" zh \\\" ]}\"}}]}", "pid", "m", null));

        AiLanguageDetectService s = new AiLanguageDetectService(cfgService, llmGateway, promptsRepository, resolver);
        List<String> langs = s.detectLanguages("  hello world  ");
        assertEquals(List.of("en", "zh"), langs);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatOnceRouted(any(), anyString(), anyString(), msgCap.capture(), any(), any(), any(), any(), any());
        assertTrue(msgCap.getValue().toString().contains("hello"));
        assertTrue(!msgCap.getValue().toString().contains("world"));
    }

    @Test
    void detectLanguages_should_fallback_raw_text_and_dedup_and_limit_to_3() {
        SemanticTranslateConfigService cfgService = mock(SemanticTranslateConfigService.class);
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(true);
        cfg.setMaxContentChars(1000);
        when(cfgService.getConfigEntityOrDefault()).thenReturn(cfg);

        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt("sys");
        p.setUserPromptTemplate("{{content}}");
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(p));

        PromptLlmParamResolver resolver = mock(PromptLlmParamResolver.class);
        when(resolver.resolveText(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any()))
                .thenReturn(new PromptLlmParams("pid", "m", 0.0, 8.0, null, false));

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatOnceRouted(any(), anyString(), anyString(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult("xx {\"languages\":[\"EN\",\"en\",\"  \",\"\\\"ZH\\\"\",\"ja\",123,\"ko\"]} yy", "pid", "m", null));

        AiLanguageDetectService s = new AiLanguageDetectService(cfgService, llmGateway, promptsRepository, resolver);
        List<String> langs = s.detectLanguages("hi");
        assertEquals(List.of("en", "zh", "ja"), langs);
        verify(promptsRepository, never()).findByPromptCode("NOT_USED");
    }

    @Test
    void detectLanguages_should_throw_wrapped_when_llm_gateway_failed() {
        SemanticTranslateConfigService cfgService = mock(SemanticTranslateConfigService.class);
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(true);
        cfg.setMaxContentChars(1000);
        when(cfgService.getConfigEntityOrDefault()).thenReturn(cfg);

        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt("sys");
        p.setUserPromptTemplate("{{content}}");
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(p));

        PromptLlmParamResolver resolver = mock(PromptLlmParamResolver.class);
        when(resolver.resolveText(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any()))
                .thenReturn(new PromptLlmParams("pid", "m", 0.0, 8.0, null, false));

        RuntimeException cause = new RuntimeException("boom");
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatOnceRouted(any(), anyString(), anyString(), anyList(), any(), any(), any(), any(), any()))
                .thenThrow(cause);

        AiLanguageDetectService s = new AiLanguageDetectService(cfgService, llmGateway, promptsRepository, resolver);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> s.detectLanguages("hi"));
        assertTrue(ex.getMessage().contains("上游AI调用失败"));
        assertSame(cause, ex.getCause());
    }

    @Test
    void detectLanguages_should_throw_when_routed_is_null_and_assistant_text_is_empty() {
        SemanticTranslateConfigService cfgService = mock(SemanticTranslateConfigService.class);
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(true);
        cfg.setMaxContentChars(1000);
        when(cfgService.getConfigEntityOrDefault()).thenReturn(cfg);

        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt("sys");
        p.setUserPromptTemplate("{{content}}");
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(p));

        PromptLlmParamResolver resolver = mock(PromptLlmParamResolver.class);
        when(resolver.resolveText(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any()))
                .thenReturn(new PromptLlmParams("pid", "m", 0.0, 8.0, null, false));

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatOnceRouted(any(), anyString(), anyString(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        AiLanguageDetectService s = new AiLanguageDetectService(cfgService, llmGateway, promptsRepository, resolver);
        assertEquals(List.of(), s.detectLanguages("hi"));
    }

    @Test
    void detectLanguages_should_fallback_to_choices_text_when_message_content_missing() {
        SemanticTranslateConfigService cfgService = mock(SemanticTranslateConfigService.class);
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(true);
        cfg.setMaxContentChars(1000);
        when(cfgService.getConfigEntityOrDefault()).thenReturn(cfg);

        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt("sys");
        p.setUserPromptTemplate("{{content}}");
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(p));

        PromptLlmParamResolver resolver = mock(PromptLlmParamResolver.class);
        when(resolver.resolveText(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any()))
                .thenReturn(new PromptLlmParams("pid", "m", 0.0, 8.0, null, false));

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatOnceRouted(any(), anyString(), anyString(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"text\":\"{\\\"languages\\\":[\\\" EN \\\",\\\"zh\\\"]}\"}]}",
                        "pid",
                        "m",
                        null
                ));

        AiLanguageDetectService s = new AiLanguageDetectService(cfgService, llmGateway, promptsRepository, resolver);
        assertEquals(List.of("en", "zh"), s.detectLanguages("hi"));
    }

    @Test
    void detectLanguages_should_use_default_template_when_prompt_template_null_and_content_null() {
        SemanticTranslateConfigService cfgService = mock(SemanticTranslateConfigService.class);
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(true);
        cfg.setMaxContentChars(null);
        when(cfgService.getConfigEntityOrDefault()).thenReturn(cfg);

        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt("sys");
        p.setUserPromptTemplate(null);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(p));

        PromptLlmParamResolver resolver = mock(PromptLlmParamResolver.class);
        when(resolver.resolveText(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any()))
                .thenReturn(new PromptLlmParams("pid", "m", 0.0, 8.0, null, false));

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatOnceRouted(any(), anyString(), anyString(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"languages\\\":[\\\"EN\\\"]}\"}}]}",
                        "pid",
                        "m",
                        null
                ));

        AiLanguageDetectService s = new AiLanguageDetectService(cfgService, llmGateway, promptsRepository, resolver);
        List<String> langs = s.detectLanguages(null);
        assertEquals(List.of("en"), langs);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatOnceRouted(any(), anyString(), anyString(), msgCap.capture(), any(), any(), any(), any(), any());
        assertTrue(msgCap.getValue().toString().contains("content="));
    }

    @Test
    void detectLanguages_should_not_truncate_when_max_chars_non_positive() {
        SemanticTranslateConfigService cfgService = mock(SemanticTranslateConfigService.class);
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(true);
        cfg.setMaxContentChars(0);
        when(cfgService.getConfigEntityOrDefault()).thenReturn(cfg);

        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt("sys");
        p.setUserPromptTemplate("{{content}}");
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(p));

        PromptLlmParamResolver resolver = mock(PromptLlmParamResolver.class);
        when(resolver.resolveText(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any()))
                .thenReturn(new PromptLlmParams("pid", "m", 0.0, 8.0, null, false));

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatOnceRouted(any(), anyString(), anyString(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"languages\\\":[\\\"EN\\\"]}\"}}]}",
                        "pid",
                        "m",
                        null
                ));

        AiLanguageDetectService s = new AiLanguageDetectService(cfgService, llmGateway, promptsRepository, resolver);
        assertEquals(List.of("en"), s.detectLanguages("abcdef"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatOnceRouted(any(), anyString(), anyString(), msgCap.capture(), any(), any(), any(), any(), any());
        assertTrue(msgCap.getValue().toString().contains("abcdef"));
    }

    @Test
    void detectLanguages_should_return_empty_when_languages_field_missing() {
        SemanticTranslateConfigService cfgService = mock(SemanticTranslateConfigService.class);
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(true);
        cfg.setMaxContentChars(1000);
        when(cfgService.getConfigEntityOrDefault()).thenReturn(cfg);

        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt("sys");
        p.setUserPromptTemplate("{{content}}");
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(p));

        PromptLlmParamResolver resolver = mock(PromptLlmParamResolver.class);
        when(resolver.resolveText(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any()))
                .thenReturn(new PromptLlmParams("pid", "m", 0.0, 8.0, null, false));

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatOnceRouted(any(), anyString(), anyString(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"notLanguages\\\":123}\"}}]}",
                        "pid",
                        "m",
                        null
                ));

        AiLanguageDetectService s = new AiLanguageDetectService(cfgService, llmGateway, promptsRepository, resolver);
        assertEquals(List.of(), s.detectLanguages("hi"));
    }

    @Test
    void detectLanguages_should_use_raw_json_when_choices_is_empty_array() {
        SemanticTranslateConfigService cfgService = mock(SemanticTranslateConfigService.class);
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(true);
        cfg.setMaxContentChars(1000);
        when(cfgService.getConfigEntityOrDefault()).thenReturn(cfg);

        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt("sys");
        p.setUserPromptTemplate("{{content}}");
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(p));

        PromptLlmParamResolver resolver = mock(PromptLlmParamResolver.class);
        when(resolver.resolveText(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any()))
                .thenReturn(new PromptLlmParams("pid", "m", 0.0, 8.0, null, false));

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatOnceRouted(any(), anyString(), anyString(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[],\"languages\":[\"EN\"]}",
                        "pid",
                        "m",
                        null
                ));

        AiLanguageDetectService s = new AiLanguageDetectService(cfgService, llmGateway, promptsRepository, resolver);
        assertEquals(List.of("en"), s.detectLanguages("hi"));
    }

    @Test
    void detectLanguages_should_trim_quote_and_cut_too_long_language_tag() {
        SemanticTranslateConfigService cfgService = mock(SemanticTranslateConfigService.class);
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(true);
        cfg.setMaxContentChars(1000);
        when(cfgService.getConfigEntityOrDefault()).thenReturn(cfg);

        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt("sys");
        p.setUserPromptTemplate("{{content}}");
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(p));

        PromptLlmParamResolver resolver = mock(PromptLlmParamResolver.class);
        when(resolver.resolveText(any(), any(), any(), any(), any(), any(), any(), anyDouble(), any()))
                .thenReturn(new PromptLlmParams("pid", "m", 0.0, 8.0, null, false));

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatOnceRouted(any(), anyString(), anyString(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"languages\\\":[\\\"  \\u201cSUPER_LONG_LANGUAGE_TAG_ABCDEFG\\u201d  \\\"]}\"}}]}",
                        "pid",
                        "m",
                        null
                ));

        AiLanguageDetectService s = new AiLanguageDetectService(cfgService, llmGateway, promptsRepository, resolver);
        assertEquals(List.of("super_long_langu"), s.detectLanguages("hi"));
    }
}
