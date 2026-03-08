package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostTagSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTagSuggestResponse;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPostTagServiceTest {

    @Test
    void suggestTags_shouldFallbackToDefaults_whenConfigValuesAreNull() {
        Fixture f = new Fixture();
        f.config.setDefaultCount(null);
        f.config.setMaxCount(null);
        f.config.setPromptCode("   ");

        PromptsEntity defaultPrompt = new PromptsEntity();
        defaultPrompt.setPromptCode(PostTagGenConfigService.DEFAULT_PROMPT_CODE);
        defaultPrompt.setSystemPrompt("sys");
        defaultPrompt.setUserPromptTemplate("count={{count}}");
        when(f.promptsRepository.findByPromptCode(PostTagGenConfigService.DEFAULT_PROMPT_CODE))
                .thenReturn(Optional.of(defaultPrompt));

        f.req.setCount(null);
        f.req.setModel("   ");
        f.req.setTemperature(null);
        f.req.setTopP(null);
        f.mockLlm("[\"A\",\"B\",\"C\",\"D\",\"E\",\"F\"]", "p1", "m1");

        AiPostTagSuggestResponse res = f.service.suggestTags(f.req, 1L);
        assertEquals(PostTagGenConfigService.DEFAULT_DEFAULT_COUNT, res.getTags().size());

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                isNull(),
                isNull(),
                anyList(),
                eq(0.4),
                eq(0.8),
                isNull(),
                isNull(),
                eq(Boolean.FALSE)
        );
    }

    @Test
    void suggestTags_shouldThrow_whenConfigDisabled() {
        Fixture f = new Fixture();
        f.config.setEnabled(Boolean.FALSE);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.service.suggestTags(f.req, 1L));
        assertTrue(ex.getMessage().contains("主题标签生成已关闭"));
    }

    @Test
    void suggestTags_shouldThrow_whenTemperatureOutOfRange() {
        Fixture f = new Fixture();
        f.req.setTemperature(-0.1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> f.service.suggestTags(f.req, 1L));
        assertTrue(ex.getMessage().contains("temperature"));
    }

    @Test
    void suggestTags_shouldThrow_whenTemperatureAboveRange() {
        Fixture f = new Fixture();
        f.req.setTemperature(2.1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> f.service.suggestTags(f.req, 1L));
        assertTrue(ex.getMessage().contains("temperature"));
    }

    @Test
    void suggestTags_shouldThrow_whenTopPOutOfRange() {
        Fixture f = new Fixture();
        f.req.setTopP(1.2);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> f.service.suggestTags(f.req, 1L));
        assertTrue(ex.getMessage().contains("topP"));
    }

    @Test
    void suggestTags_shouldThrow_whenTopPBelowRange() {
        Fixture f = new Fixture();
        f.req.setTopP(-0.01);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> f.service.suggestTags(f.req, 1L));
        assertTrue(ex.getMessage().contains("topP"));
    }

    @Test
    void suggestTags_shouldThrow_whenPromptNotFound() {
        Fixture f = new Fixture();
        when(f.promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.service.suggestTags(f.req, 1L));
        assertTrue(ex.getMessage().contains("Prompt code not found"));
    }

    @Test
    void suggestTags_shouldWrapException_whenLlmGatewayFails() {
        Fixture f = new Fixture();
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), isNull(), isNull(), any()))
                .thenThrow(new RuntimeException("boom"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.service.suggestTags(f.req, 1L));
        assertTrue(ex.getMessage().contains("上游AI调用失败"));
        assertNotNull(ex.getCause());
    }

    @Test
    void suggestTags_shouldClampCountAndContentAndFallbackTemperatureTopP() {
        Fixture f = new Fixture();
        f.config.setDefaultCount(3);
        f.config.setMaxCount(5);
        f.config.setMaxContentChars(10);
        f.prompt.setUserPromptTemplate("n={{count}}\\n{{content}}");

        f.req.setCount(99);
        f.req.setContent("1234567890ABCDEFG");
        f.req.setTemperature(null);
        f.req.setTopP(null);

        f.mockLlm("[\"A\",\"B\",\"C\",\"D\",\"E\",\"F\"]", "p1", "m1");

        AiPostTagSuggestResponse res = f.service.suggestTags(f.req, 1L);
        assertEquals(5, res.getTags().size());

        ArgumentCaptor<List<ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Double> temperatureCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> topPCaptor = ArgumentCaptor.forClass(Double.class);
        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                eq("provider-1"),
                eq("cfg-model"),
                messagesCaptor.capture(),
                temperatureCaptor.capture(),
                topPCaptor.capture(),
                isNull(),
                isNull(),
                eq(Boolean.FALSE)
        );

        assertEquals(0.4, temperatureCaptor.getValue());
        assertEquals(0.8, topPCaptor.getValue());
        String userPrompt = (String) messagesCaptor.getValue().get(1).content();
        assertTrue(userPrompt.contains("n=5"));
        assertTrue(userPrompt.contains("1234567890"));
    }

    @Test
    void suggestTags_shouldUseServiceDefaultCounts_whenConfigCountsNull() {
        Fixture f = new Fixture();
        f.config.setDefaultCount(null);
        f.config.setMaxCount(null);
        f.prompt.setUserPromptTemplate("n={{count}}");
        f.req.setCount(null);
        f.mockLlm("[\"A\",\"B\",\"C\",\"D\",\"E\",\"F\"]", "p1", "m1");

        AiPostTagSuggestResponse res = f.service.suggestTags(f.req, 1L);
        assertEquals(PostTagGenConfigService.DEFAULT_DEFAULT_COUNT, res.getTags().size());
    }

    @Test
    void suggestTags_shouldNotTruncateContent_whenMaxCharsNonPositive() {
        Fixture f = new Fixture();
        f.config.setMaxContentChars(0);
        f.prompt.setUserPromptTemplate("{{content}}");
        f.req.setContent("ABCDEFGHIJKLMN");
        f.mockLlm("[\"A\"]", "p1", "m1");

        f.service.suggestTags(f.req, 1L);

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                eq("provider-1"),
                eq("cfg-model"),
                argThat(messages -> {
                    Object content = messages.get(1).content();
                    return content instanceof String s && s.contains("ABCDEFGHIJKLMN");
                }),
                any(),
                any(),
                isNull(),
                isNull(),
                eq(Boolean.FALSE)
        );
    }

    @Test
    void suggestTags_shouldFallbackToDefaultCount_whenRequestCountNotPositive() {
        Fixture f = new Fixture();
        f.config.setDefaultCount(4);
        f.config.setMaxCount(10);
        f.prompt.setUserPromptTemplate("count={{count}}");
        f.req.setCount(0);
        f.mockLlm("[\"A\",\"B\",\"C\",\"D\",\"E\"]", "p1", "m1");

        AiPostTagSuggestResponse res = f.service.suggestTags(f.req, 1L);
        assertEquals(4, res.getTags().size());

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                eq("provider-1"),
                eq("cfg-model"),
                argThat(messages -> {
                    Object content = messages.get(1).content();
                    return content instanceof String s && s.contains("count=4");
                }),
                any(),
                any(),
                isNull(),
                isNull(),
                eq(Boolean.FALSE)
        );
    }

    @Test
    void suggestTags_shouldFallbackToDefaultCount_whenRequestCountNegative() {
        Fixture f = new Fixture();
        f.config.setDefaultCount(4);
        f.config.setMaxCount(10);
        f.prompt.setUserPromptTemplate("count={{count}}");
        f.req.setCount(-7);
        f.mockLlm("[\"A\",\"B\",\"C\",\"D\",\"E\"]", "p1", "m1");

        AiPostTagSuggestResponse res = f.service.suggestTags(f.req, 1L);
        assertEquals(4, res.getTags().size());

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                eq("provider-1"),
                eq("cfg-model"),
                argThat(messages -> {
                    Object content = messages.get(1).content();
                    return content instanceof String s && s.contains("count=4");
                }),
                any(),
                any(),
                isNull(),
                isNull(),
                eq(Boolean.FALSE)
        );
    }

    @Test
    void suggestTags_shouldUseDefaultPromptCode_whenConfigPromptCodeBlank() {
        Fixture f = new Fixture();
        f.config.setPromptCode("   ");
        f.mockLlm("[\"A\"]", "p1", "m1");

        f.service.suggestTags(f.req, 1L);

        verify(f.promptsRepository, times(1)).findByPromptCode(PostTagGenConfigService.DEFAULT_PROMPT_CODE);
    }

    @Test
    void suggestTags_shouldUseDefaultPromptCode_whenConfigPromptCodeNull() {
        Fixture f = new Fixture();
        f.config.setPromptCode(null);
        f.mockLlm("[\"A\"]", "p1", "m1");

        f.service.suggestTags(f.req, 1L);

        verify(f.promptsRepository, times(1)).findByPromptCode(PostTagGenConfigService.DEFAULT_PROMPT_CODE);
    }

    @Test
    void suggestTags_shouldHandleNullContentAndNullConfigBranches() {
        Fixture f = new Fixture();
        f.config.setPromptCode(null);
        f.config.setMaxContentChars(null);

        PromptsEntity defaultPrompt = new PromptsEntity();
        defaultPrompt.setPromptCode(PostTagGenConfigService.DEFAULT_PROMPT_CODE);
        defaultPrompt.setSystemPrompt("sys");
        defaultPrompt.setUserPromptTemplate("content={{content}}");
        when(f.promptsRepository.findByPromptCode(PostTagGenConfigService.DEFAULT_PROMPT_CODE))
                .thenReturn(Optional.of(defaultPrompt));

        f.req.setContent(null);
        f.req.setModel("   ");
        f.mockLlm("[\"A\"]", "p1", "m1");

        AiPostTagSuggestResponse res = f.service.suggestTags(f.req, 1L);
        assertEquals(List.of("A"), res.getTags());

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                isNull(),
                isNull(),
                anyList(),
                any(),
                any(),
                isNull(),
                isNull(),
                any()
        );
    }

    @Test
    void suggestTags_shouldUseConfigTemperatureAndTopP_whenRequestValuesNull() {
        Fixture f = new Fixture();
        f.prompt.setTemperature(1.2);
        f.prompt.setTopP(0.33);
        f.req.setTemperature(null);
        f.req.setTopP(null);
        f.mockLlm("[\"A\"]", "p1", "m1");

        f.service.suggestTags(f.req, 1L);

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                eq("provider-1"),
                any(),
                anyList(),
                eq(1.2),
                eq(0.33),
                isNull(),
                isNull(),
                eq(Boolean.FALSE)
        );
    }

    @Test
    void suggestTags_shouldAcceptBoundaryTemperatureAndTopPValues() {
        Fixture f1 = new Fixture();
        f1.req.setTemperature(0.0);
        f1.req.setTopP(0.0);
        f1.mockLlm("[\"A\"]", "p1", "m1");
        f1.service.suggestTags(f1.req, 1L);
        verify(f1.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                any(),
                any(),
                anyList(),
                eq(0.0),
                eq(0.0),
                isNull(),
                isNull(),
                any()
        );

        Fixture f2 = new Fixture();
        f2.req.setTemperature(2.0);
        f2.req.setTopP(1.0);
        f2.mockLlm("[\"A\"]", "p1", "m1");
        f2.service.suggestTags(f2.req, 1L);
        verify(f2.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                any(),
                any(),
                anyList(),
                eq(2.0),
                eq(1.0),
                isNull(),
                isNull(),
                any()
        );
    }

    @Test
    void suggestTags_shouldPassEnableThinkingTrueToGateway() {
        Fixture f = new Fixture();
        f.prompt.setTemperature(0.6);
        f.prompt.setTopP(0.7);
        f.prompt.setEnableDeepThinking(true);
        f.mockLlm("[\"A\"]", "p1", "m1");

        f.service.suggestTags(f.req, 1L);

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                eq("provider-1"),
                eq("cfg-model"),
                anyList(),
                eq(0.6),
                eq(0.7),
                isNull(),
                isNull(),
                eq(Boolean.TRUE)
        );
    }

    @Test
    void suggestTags_shouldUseReqModelOverrideFirst() {
        Fixture f = new Fixture();
        f.req.setModel("  req-model  ");
        f.mockLlm("[\"A\"]", "p1", "returned-model");

        f.service.suggestTags(f.req, 1L);

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                eq("provider-1"),
                eq("req-model"),
                anyList(),
                any(),
                any(),
                isNull(),
                isNull(),
                eq(Boolean.FALSE)
        );
    }

    @Test
    void suggestTags_shouldUseConfigModelWhenReqBlank() {
        Fixture f = new Fixture();
        f.prompt.setModelName("cfg-only");
        f.req.setModel("   ");
        f.mockLlm("[\"A\"]", "p1", "returned-model");

        f.service.suggestTags(f.req, 1L);

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                eq("provider-1"),
                eq("cfg-only"),
                anyList(),
                any(),
                any(),
                isNull(),
                isNull(),
                eq(Boolean.FALSE)
        );
    }

    @Test
    void suggestTags_shouldPassNullModelWhenBothReqAndConfigBlank() {
        Fixture f = new Fixture();
        f.prompt.setModelName(null);
        f.req.setModel(" ");
        f.mockLlm("[\"A\"]", "p1", "returned-model");

        f.service.suggestTags(f.req, 1L);

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                eq("provider-1"),
                isNull(),
                anyList(),
                any(),
                any(),
                isNull(),
                isNull(),
                eq(Boolean.FALSE)
        );
    }

    @Test
    void suggestTags_shouldRenderPromptWithOptionalLines() {
        Fixture f = new Fixture();
        f.prompt.setUserPromptTemplate("{{boardLine}}{{titleLine}}{{tagsLine}}内容={{content}}");
        f.req.setBoardName("  Java  ");
        f.req.setTitle("  标题  ");
        f.req.setTags(List.of("  A  ", " ", "B"));
        f.mockLlm("[\"A\"]", "p1", "m1");

        f.service.suggestTags(f.req, 1L);

        ArgumentCaptor<List<ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                any(),
                any(),
                messagesCaptor.capture(),
                any(),
                any(),
                isNull(),
                isNull(),
                any()
        );
        String prompt = (String) messagesCaptor.getValue().get(1).content();
        assertTrue(prompt.contains("版块：Java"));
        assertTrue(prompt.contains("标题：标题"));
        assertTrue(prompt.contains("已有标签：A、B"));
    }

    @Test
    void suggestTags_shouldRenderPromptWithoutOptionalLines_whenValuesBlank() {
        Fixture f = new Fixture();
        f.prompt.setUserPromptTemplate("{{boardLine}}{{titleLine}}{{tagsLine}}END");
        f.req.setBoardName("   ");
        f.req.setTitle(" ");
        f.req.setTags(Arrays.asList("  ", null));
        f.mockLlm("[\"A\"]", "p1", "m1");

        f.service.suggestTags(f.req, 1L);

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                any(),
                any(),
                argThat(messages -> {
                    Object content = messages.get(1).content();
                    if (!(content instanceof String s)) return false;
                    return s.contains("END")
                            && !s.contains("版块：")
                            && !s.contains("标题：")
                            && !s.contains("已有标签：");
                }),
                any(),
                any(),
                isNull(),
                isNull(),
                any()
        );
    }

    @Test
    void suggestTags_shouldRenderEmptyPrompt_whenTemplateBlank() {
        Fixture f = new Fixture();
        f.prompt.setUserPromptTemplate("   ");
        f.mockLlm("[\"A\"]", "p1", "m1");

        f.service.suggestTags(f.req, 1L);

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                any(),
                any(),
                argThat(messages -> {
                    Object content = messages.get(1).content();
                    return content instanceof String s && s.isEmpty();
                }),
                any(),
                any(),
                isNull(),
                isNull(),
                any()
        );
    }

    @Test
    void suggestTags_shouldExtractAssistantContent_fromMessageContent_text_andRawFallback() {
        Fixture f1 = new Fixture();
        f1.mockLlm("{\"choices\":[{\"message\":{\"content\":\"[\\\"M1\\\"]\"}}]}", "p1", "m1");
        AiPostTagSuggestResponse r1 = f1.service.suggestTags(f1.req, 1L);
        assertEquals(List.of("M1"), r1.getTags());

        Fixture f2 = new Fixture();
        f2.mockLlm("{\"choices\":[{\"text\":\"[\\\"T1\\\"]\"}]}", "p1", "m1");
        AiPostTagSuggestResponse r2 = f2.service.suggestTags(f2.req, 1L);
        assertEquals(List.of("T1"), r2.getTags());

        Fixture f3 = new Fixture();
        f3.mockLlm("[\"R1\"]", "p1", "m1");
        AiPostTagSuggestResponse r3 = f3.service.suggestTags(f3.req, 1L);
        assertEquals(List.of("R1"), r3.getTags());
    }

    @Test
    void suggestTags_shouldFallbackToRaw_whenChoicesEmpty() {
        Fixture f = new Fixture();
        f.mockLlm("{\"choices\":[],\"raw\":\"[\\\"A1\\\"]\"}[\"A1\"]", "p1", "m1");

        AiPostTagSuggestResponse res = f.service.suggestTags(f.req, 1L);
        assertTrue(res.getTags().isEmpty());
    }

    @Test
    void suggestTags_shouldFallbackRaw_whenChoicesEmptyOrNonTextual() {
        Fixture f1 = new Fixture();
        f1.mockLlm("{\"choices\":[]}", "p1", "m1");
        AiPostTagSuggestResponse r1 = f1.service.suggestTags(f1.req, 1L);
        assertTrue(r1.getTags().isEmpty());

        Fixture f2 = new Fixture();
        f2.mockLlm("{\"choices\":[{\"message\":{\"content\":{}},\"text\":123}]}", "p1", "m1");
        AiPostTagSuggestResponse r2 = f2.service.suggestTags(f2.req, 1L);
        assertTrue(r2.getTags().isEmpty());

        Fixture f3 = new Fixture();
        f3.mockLlm("{\"choices\":[{\"message\":{\"content\":{}}}]}", "p1", "m1");
        AiPostTagSuggestResponse r3 = f3.service.suggestTags(f3.req, 1L);
        assertTrue(r3.getTags().isEmpty());
    }

    @Test
    void suggestTags_shouldRenderWithoutTagsLine_whenTagsIsEmptyList() {
        Fixture f = new Fixture();
        f.prompt.setUserPromptTemplate("{{tagsLine}}END");
        f.req.setTags(List.of());
        f.mockLlm("[\"A\"]", "p1", "m1");

        f.service.suggestTags(f.req, 1L);

        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                any(),
                any(),
                argThat(messages -> {
                    Object content = messages.get(1).content();
                    if (!(content instanceof String s)) return false;
                    return s.contains("END") && !s.contains("已有标签：");
                }),
                any(),
                any(),
                isNull(),
                isNull(),
                any()
        );
    }

    @Test
    void suggestTags_shouldReturnEmptyTags_whenRoutedResultIsNull() {
        Fixture f = new Fixture();
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), isNull(), isNull(), any()))
                .thenReturn(null);

        AiPostTagSuggestResponse res = f.service.suggestTags(f.req, 1L);
        assertNotNull(res);
        assertTrue(res.getTags().isEmpty());
        assertNull(res.getModel());
    }

    @Test
    void suggestTags_shouldHandleMalformedRawJsonByFallbackAndStillParseArray() {
        Fixture f = new Fixture();
        f.mockLlm("prefix [\"A\",\"B\"] suffix", "p1", "m1");

        AiPostTagSuggestResponse res = f.service.suggestTags(f.req, 1L);
        assertEquals(List.of("A", "B"), res.getTags());
    }

    @Test
    void suggestTags_shouldNotTruncateContentWhenMaxCharsNotPositive() {
        Fixture f = new Fixture();
        f.config.setMaxContentChars(0);
        f.prompt.setUserPromptTemplate("{{content}}");
        f.req.setContent("ABCDEFGHIJK");
        f.mockLlm("[\"A\"]", "p1", "m1");

        f.service.suggestTags(f.req, 1L);

        ArgumentCaptor<List<ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(f.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TOPIC_TAG_GEN),
                any(),
                any(),
                messagesCaptor.capture(),
                any(),
                any(),
                isNull(),
                isNull(),
                any()
        );
        String userPrompt = String.valueOf(messagesCaptor.getValue().get(1).content());
        assertTrue(userPrompt.contains("ABCDEFGHIJK"));
    }

    @Test
    void suggestTags_shouldReturnEmptyTags_whenRoutedTextIsNull() {
        Fixture f = new Fixture();
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), isNull(), isNull(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(null, "p1", "m1", null));

        AiPostTagSuggestResponse res = f.service.suggestTags(f.req, 1L);
        assertNotNull(res);
        assertTrue(res.getTags().isEmpty());
        assertEquals("m1", res.getModel());
    }

    @Test
    void suggestTags_shouldRecordHistory_whenHistoryEnabledAndActorPresent() {
        Fixture f = new Fixture();
        f.config.setHistoryEnabled(Boolean.TRUE);
        f.config.setVersion(7);
        f.req.setBoardName("   ");
        f.req.setTitle("T".repeat(140));
        f.req.setContent("C".repeat(300));
        f.mockLlm("[\"A\"]", "provider-x", "model-x");

        f.service.suggestTags(f.req, 99L);

        ArgumentCaptor<PostSuggestionGenHistoryEntity> captor = ArgumentCaptor.forClass(PostSuggestionGenHistoryEntity.class);
        verify(f.postTagGenConfigService, times(1)).recordHistory(captor.capture());
        PostSuggestionGenHistoryEntity h = captor.getValue();
        assertEquals(99L, h.getUserId());
        assertNull(h.getBoardName());
        assertEquals(120, h.getTitleExcerpt().length());
        assertEquals(240, h.getContentExcerpt().length());
        assertEquals(List.of("A"), h.getOutputJson());
    }

    @Test
    void suggestTags_shouldRecordHistory_withTrimmedBoardAndNullExcerpts() {
        Fixture f = new Fixture();
        f.config.setHistoryEnabled(Boolean.TRUE);
        f.req.setBoardName("  Java  ");
        f.req.setTitle("   ");
        f.req.setContent("   ");
        f.mockLlm("[\"A\"]", "provider-x", "model-x");

        f.service.suggestTags(f.req, 88L);

        ArgumentCaptor<PostSuggestionGenHistoryEntity> captor = ArgumentCaptor.forClass(PostSuggestionGenHistoryEntity.class);
        verify(f.postTagGenConfigService, times(1)).recordHistory(captor.capture());
        PostSuggestionGenHistoryEntity h = captor.getValue();
        assertEquals("Java", h.getBoardName());
        assertNull(h.getTitleExcerpt());
        assertNull(h.getContentExcerpt());
    }

    @Test
    void suggestTags_shouldRecordHistoryCoreNumbers_afterCountClampAndContentTrim() {
        Fixture f = new Fixture();
        f.config.setHistoryEnabled(Boolean.TRUE);
        f.config.setDefaultCount(3);
        f.config.setMaxCount(4);
        f.config.setMaxContentChars(5);
        f.req.setCount(99);
        f.req.setContent(" 123456789 ");
        f.mockLlm("[\"A\",\"B\",\"C\",\"D\",\"E\"]", "p1", "m1");

        f.service.suggestTags(f.req, 7L);

        ArgumentCaptor<PostSuggestionGenHistoryEntity> captor = ArgumentCaptor.forClass(PostSuggestionGenHistoryEntity.class);
        verify(f.postTagGenConfigService).recordHistory(captor.capture());
        PostSuggestionGenHistoryEntity h = captor.getValue();
        assertEquals(4, h.getRequestedCount());
        assertEquals(5, h.getAppliedMaxContentChars());
        assertEquals(9, h.getContentLen());
    }

    @Test
    void suggestTags_shouldSkipHistory_whenHistoryDisabledOrActorNull() {
        Fixture f1 = new Fixture();
        f1.config.setHistoryEnabled(Boolean.FALSE);
        f1.mockLlm("[\"A\"]", "p1", "m1");
        f1.service.suggestTags(f1.req, 1L);
        verify(f1.postTagGenConfigService, never()).recordHistory(any());

        Fixture f2 = new Fixture();
        f2.config.setHistoryEnabled(Boolean.TRUE);
        f2.mockLlm("[\"A\"]", "p1", "m1");
        f2.service.suggestTags(f2.req, null);
        verify(f2.postTagGenConfigService, never()).recordHistory(any());
    }

    @Test
    void suggestTags_shouldRecordNullTitleExcerptAndContentExcerpt_whenBlankInputs() {
        Fixture f = new Fixture();
        f.config.setHistoryEnabled(Boolean.TRUE);
        f.req.setBoardName("  BoardX ");
        f.req.setTitle("   ");
        f.req.setContent("   ");
        f.mockLlm("[\"A\"]", "provider-x", "model-x");

        f.service.suggestTags(f.req, 11L);

        ArgumentCaptor<PostSuggestionGenHistoryEntity> captor = ArgumentCaptor.forClass(PostSuggestionGenHistoryEntity.class);
        verify(f.postTagGenConfigService).recordHistory(captor.capture());
        PostSuggestionGenHistoryEntity h = captor.getValue();
        assertEquals("BoardX", h.getBoardName());
        assertNull(h.getTitleExcerpt());
        assertNull(h.getContentExcerpt());
    }

    @Test
    void parseTags_shouldParseObjectAndArraySlices_fromNoisyText() {
        Fixture f = new Fixture();
        List<String> r1 = f.service.parseTagsFromAssistantText("noise ```json {\"tags\":[\"A\",\"B\"]} ```", 5);
        assertEquals(List.of("A", "B"), r1);

        List<String> r2 = f.service.parseTagsFromAssistantText("header [\"C\",\"D\"] tail", 5);
        assertEquals(List.of("C", "D"), r2);
    }

    @Test
    void parseTags_shouldPreferObjectSlice_whenObjectAppearsBeforeArray() {
        Fixture f = new Fixture();
        String mixed = "pre {\"tags\":[\"OBJ\"]} mid [\"ARR\"] post";

        List<String> out = f.service.parseTagsFromAssistantText(mixed, 5);
        assertEquals(List.of("OBJ"), out);
    }

    @Test
    void parseTags_shouldPreferArraySlice_whenArrayAppearsBeforeObject() {
        Fixture f = new Fixture();
        String mixed = "pre [\"ARR\"] mid {\"tags\":[\"OBJ\"]} post";

        List<String> out = f.service.parseTagsFromAssistantText(mixed, 5);
        assertEquals(List.of("ARR"), out);
    }

    @Test
    void parseTags_shouldDropNonTextDeduplicateAndTruncate() {
        Fixture f = new Fixture();
        String input = "{\"tags\":[\" \\\"A\\\" \",\"A\",\"\",\"   \",\"“超长标签01234567890123456789”\",123]}";

        List<String> out = f.service.parseTagsFromAssistantText(input, 2);
        assertEquals(2, out.size());
        assertEquals("A", out.get(0));
        assertEquals(20, out.get(1).length());
        assertTrue(out.get(1).startsWith("超长标签0123"));
    }

    @Test
    void parseTags_shouldReturnEmpty_whenObjectHasNoTagsArray() {
        Fixture f = new Fixture();

        List<String> out = f.service.parseTagsFromAssistantText("{\"foo\":\"bar\"}", 3);
        assertTrue(out.isEmpty());
    }

    @Test
    void parseTags_shouldThrowIllegalArgumentException_onInvalidJson() {
        Fixture f = new Fixture();

        assertThrows(IllegalArgumentException.class, () -> f.service.parseTagsFromAssistantText("not-json", 3));
    }

    @Test
    void parseTags_shouldHandleNullInput_asEmptyAndReturnEmptyList() {
        Fixture f = new Fixture();

        List<String> out = f.service.parseTagsFromAssistantText(null, 3);
        assertEquals(List.of(), out);
    }

    @Test
    void parseTags_shouldHandleBlankInput_asEmptyAndReturnEmptyList() {
        Fixture f = new Fixture();

        List<String> out = f.service.parseTagsFromAssistantText("   ", 3);
        assertEquals(List.of(), out);
    }

    @Test
    void parseTags_shouldReturnEmpty_whenExpectedCountIsZero() {
        Fixture f = new Fixture();
        List<String> out = f.service.parseTagsFromAssistantText("[\"A\",\"B\"]", 0);
        assertEquals(List.of(), out);
    }

    @Test
    void parseTags_shouldThrowWhenExpectedCountNegative() {
        Fixture f = new Fixture();

        assertThrows(IndexOutOfBoundsException.class,
                () -> f.service.parseTagsFromAssistantText("[\"A\",\"B\"]", -1));
    }

    @Test
    void parseTags_shouldHandleArrayBeforeObjectAndNoJsonSlice() {
        Fixture f = new Fixture();

        List<String> out1 = f.service.parseTagsFromAssistantText("[\"A\"] tail {\"tags\":[\"B\"]}", 5);
        assertEquals(List.of("A"), out1);

        List<String> out2 = f.service.parseTagsFromAssistantText("\"plain-text\"", 5);
        assertTrue(out2.isEmpty());
    }

    @Test
    void parseTags_shouldThrow_onUnclosedObjectOrArraySlices() {
        Fixture f = new Fixture();

        assertThrows(IllegalArgumentException.class,
                () -> f.service.parseTagsFromAssistantText("prefix {\"tags\":[\"A\"]", 5));

        assertThrows(IllegalArgumentException.class,
                () -> f.service.parseTagsFromAssistantText("prefix {\"tags\":[\"A\"", 5));
    }

    @Test
    void parseTags_shouldThrow_onUnclosedArraySliceOnly() {
        Fixture f = new Fixture();
        assertThrows(IllegalArgumentException.class,
                () -> f.service.parseTagsFromAssistantText("prefix [\"A\"", 5));
    }

    @Test
    void privateHelpers_shouldCoverNullBranches() throws Exception {
        Fixture f = new Fixture();

        Object cleanNull = invokePrivate(f.service, "cleanTag", new Class[]{String.class}, new Object[]{null});
        assertEquals("", cleanNull);

        Object excerptNull = invokePrivateStatic(AiPostTagService.class, "buildExcerpt", new Class[]{String.class}, new Object[]{null});
        assertNull(excerptNull);

        Object rendered = invokePrivateStatic(
                AiPostTagService.class,
                "renderPrompt",
                new Class[]{String.class, int.class, String.class, String.class, List.class, String.class},
                new Object[]{null, 3, null, null, null, null}
        );
        assertEquals("", rendered);
    }

    private static Object invokePrivate(Object target, String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object invokePrivateStatic(Class<?> type, String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method method = type.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static class Fixture {
        private final PostTagGenConfigService postTagGenConfigService = mock(PostTagGenConfigService.class);
        private final LlmGateway llmGateway = mock(LlmGateway.class);
        private final PromptsRepository promptsRepository = mock(PromptsRepository.class);
        private final com.example.EnterpriseRagCommunity.repository.semantic.GenerationJobsRepository generationJobsRepository = mock(com.example.EnterpriseRagCommunity.repository.semantic.GenerationJobsRepository.class);
        private final AiPostTagService service = new AiPostTagService(postTagGenConfigService, llmGateway, promptsRepository, generationJobsRepository, new PromptLlmParamResolver());
        private final PostSuggestionGenConfigEntity config = baseConfig();
        private final PromptsEntity prompt = basePrompt();
        private final AiPostTagSuggestRequest req = baseRequest();

        private Fixture() {
            when(postTagGenConfigService.getConfigEntityOrDefault()).thenReturn(config);
            when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt));
            mockLlm("[\"A\"]", "provider-1", "model-1");
        }

        private void mockLlm(String raw, String providerId, String model) {
            when(llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), isNull(), isNull(), any()))
                    .thenReturn(new LlmGateway.RoutedChatOnceResult(raw, providerId, model, null));
        }
    }

    private static PostSuggestionGenConfigEntity baseConfig() {
        PostSuggestionGenConfigEntity c = new PostSuggestionGenConfigEntity();
        c.setEnabled(Boolean.TRUE);
        c.setDefaultCount(5);
        c.setMaxCount(10);
        c.setMaxContentChars(4000);
        c.setHistoryEnabled(Boolean.TRUE);
        c.setPromptCode("TAG_GEN");
        c.setVersion(3);
        return c;
    }

    private static PromptsEntity basePrompt() {
        PromptsEntity p = new PromptsEntity();
        p.setPromptCode("TAG_GEN");
        p.setSystemPrompt("sys");
        p.setUserPromptTemplate("{{count}}\\n{{boardLine}}{{titleLine}}{{tagsLine}}{{content}}");
        p.setProviderId("provider-1");
        p.setModelName("cfg-model");
        return p;
    }

    private static AiPostTagSuggestRequest baseRequest() {
        AiPostTagSuggestRequest r = new AiPostTagSuggestRequest();
        r.setContent("hello world");
        r.setCount(3);
        r.setBoardName(null);
        r.setTitle(null);
        r.setTags(null);
        return r;
    }
}
