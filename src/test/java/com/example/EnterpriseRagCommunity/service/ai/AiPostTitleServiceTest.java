package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestResponse;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPostTitleServiceTest {

    @Test
    void suggestTitles_shouldFallbackToSystemDefaultsWhenCfgValuesNull() {
        TestFixture fixture = TestFixture.create();
        fixture.cfg.setDefaultCount(null);
        fixture.cfg.setMaxCount(null);
        fixture.cfg.setPromptCode("  ");

        PromptsEntity fallbackPrompt = new PromptsEntity();
        fallbackPrompt.setPromptCode(PostTitleGenConfigService.DEFAULT_PROMPT_CODE);
        fallbackPrompt.setSystemPrompt("sys-default");
        fallbackPrompt.setUserPromptTemplate("count={{count}}\\n内容={{content}}");
        when(fixture.promptsRepository.findByPromptCode(PostTitleGenConfigService.DEFAULT_PROMPT_CODE))
                .thenReturn(Optional.of(fallbackPrompt));

        AiPostTitleSuggestRequest req = fixture.baseRequest();
        req.setCount(null);
        req.setModel(" ");
        req.setTemperature(null);
        req.setTopP(null);

        when(fixture.llmGateway.chatOnceRouted(
                eq(LlmQueueTaskType.TITLE_GEN),
                isNull(),
                isNull(),
                any(),
                eq(0.4),
                eq(0.9),
                eq(null),
                eq(null),
                eq(false)
        )).thenReturn(new LlmGateway.RoutedChatOnceResult(
                "{\"choices\":[{\"message\":{\"content\":\"[\\\"D1\\\",\\\"D2\\\"]\"}}]}",
                "p", "m", null
        ));

        AiPostTitleSuggestResponse response = fixture.service.suggestTitles(req, null);
        assertEquals(List.of("D1", "D2"), response.getTitles());
    }

    @Test
    void suggestTitles_shouldThrowWhenDisabled() {
        TestFixture fixture = TestFixture.create();
        fixture.cfg.setEnabled(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fixture.service.suggestTitles(fixture.baseRequest(), 1L));
        assertTrue(ex.getMessage().contains("标题生成已关闭"));
    }

    @Test
    void suggestTitles_shouldThrowWhenTemperatureOutOfRange() {
        TestFixture fixture = TestFixture.create();
        AiPostTitleSuggestRequest req = fixture.baseRequest();
        req.setTemperature(2.5);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fixture.service.suggestTitles(req, 1L));
        assertTrue(ex.getMessage().contains("temperature"));
    }

    @Test
    void suggestTitles_shouldThrowWhenTemperatureBelowZero() {
        TestFixture fixture = TestFixture.create();
        AiPostTitleSuggestRequest req = fixture.baseRequest();
        req.setTemperature(-0.1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fixture.service.suggestTitles(req, 1L));
        assertTrue(ex.getMessage().contains("temperature"));
    }

    @Test
    void suggestTitles_shouldThrowWhenTopPOutOfRange() {
        TestFixture fixture = TestFixture.create();
        AiPostTitleSuggestRequest req = fixture.baseRequest();
        req.setTopP(1.5);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fixture.service.suggestTitles(req, 1L));
        assertTrue(ex.getMessage().contains("topP"));
    }

    @Test
    void suggestTitles_shouldThrowWhenTopPBelowZero() {
        TestFixture fixture = TestFixture.create();
        AiPostTitleSuggestRequest req = fixture.baseRequest();
        req.setTopP(-0.01);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fixture.service.suggestTitles(req, 1L));
        assertTrue(ex.getMessage().contains("topP"));
    }

    @Test
    void suggestTitles_shouldNormalizeCountAndUseRequestModel() {
        TestFixture fixture = TestFixture.create();
        fixture.cfg.setDefaultCount(4);
        fixture.cfg.setMaxCount(7);

        AiPostTitleSuggestRequest req = fixture.baseRequest();
        req.setCount(999);
        req.setModel(" req-model ");

        when(fixture.llmGateway.chatOnceRouted(
                eq(LlmQueueTaskType.TITLE_GEN),
                eq("pid-1"),
                eq("req-model"),
                any(),
                eq(0.6),
                eq(0.7),
                eq(null),
                eq(null),
                eq(false)
        )).thenReturn(new LlmGateway.RoutedChatOnceResult(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"titles\\\":[\\\"\\\\\\\"A\\\\\\\"\\\",\\\"B\\\",\\\"B\\\",\\\"C\\\"]}\"}}]}",
                "prov-x",
                "m-x",
                null
        ));

        AiPostTitleSuggestResponse response = fixture.service.suggestTitles(req, null);
        assertEquals(List.of("A", "B", "C"), response.getTitles());
        assertEquals("m-x", response.getModel());
    }

    @Test
    void suggestTitles_shouldUseDefaultCountWhenRequestCountNegative() {
        TestFixture fixture = TestFixture.create();
        fixture.cfg.setDefaultCount(4);
        fixture.cfg.setMaxCount(7);

        AiPostTitleSuggestRequest req = fixture.baseRequest();
        req.setCount(-3);

        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"[\\\"T1\\\"]\"}}]}",
                        "p", "m", null
                ));

        fixture.service.suggestTitles(req, null);

        ArgumentCaptor<List> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(fixture.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TITLE_GEN),
                eq("pid-1"),
                eq("cfg-model"),
                messagesCaptor.capture(),
                eq(0.6),
                eq(0.7),
                eq(null),
                eq(null),
                eq(false)
        );

        @SuppressWarnings("unchecked")
        List<ChatMessage> messages = messagesCaptor.getValue();
        String userPrompt = String.valueOf(messages.get(1).content());
        assertTrue(userPrompt.contains("count=4"));
    }

    @Test
    void suggestTitles_shouldFallbackToCfgModelAndRecordHistory() {
        TestFixture fixture = TestFixture.create();
        fixture.cfg.setHistoryEnabled(true);
        fixture.cfg.setDefaultCount(5);
        fixture.cfg.setMaxCount(10);
        fixture.cfg.setMaxContentChars(20);
        fixture.cfg.setVersion(12);

        AiPostTitleSuggestRequest req = fixture.baseRequest();
        req.setCount(0);
        req.setModel("   ");
        req.setBoardName("  ");
        req.setTags(List.of("  ", "tag1"));
        req.setContent("   012345678901234567890123456789   ");

        when(fixture.llmGateway.chatOnceRouted(
                eq(LlmQueueTaskType.TITLE_GEN),
                eq("pid-1"),
                eq("cfg-model"),
                any(),
                eq(0.6),
                eq(0.7),
                eq(null),
                eq(null),
                eq(false)
        )).thenReturn(new LlmGateway.RoutedChatOnceResult(
                "{\"choices\":[{\"message\":{\"content\":\"[\\\"T1\\\",\\\"T2\\\"]\"}}]}",
                "prov-2",
                "model-2",
                null
        ));

        AiPostTitleSuggestResponse response = fixture.service.suggestTitles(req, 42L);
        assertEquals(List.of("T1", "T2"), response.getTitles());

        ArgumentCaptor<PostSuggestionGenHistoryEntity> historyCaptor = ArgumentCaptor.forClass(PostSuggestionGenHistoryEntity.class);
        verify(fixture.configService).recordHistory(historyCaptor.capture());
        PostSuggestionGenHistoryEntity history = historyCaptor.getValue();
        assertEquals(5, history.getRequestedCount());
        assertEquals(20, history.getAppliedMaxContentChars());
        assertEquals(30, history.getContentLen());
        assertEquals("012345678901234567890123456789", history.getContentExcerpt());
        assertNull(history.getBoardName());
        assertNotNull(history.getCreatedAt());
    }

    @Test
    void suggestTitles_shouldNotRecordHistoryWhenDisabledOrActorMissing() {
        TestFixture fixture = TestFixture.create();
        fixture.cfg.setHistoryEnabled(false);

        when(fixture.llmGateway.chatOnceRouted(
                eq(LlmQueueTaskType.TITLE_GEN),
                eq("pid-1"),
                eq("cfg-model"),
                any(),
                eq(0.6),
                eq(0.7),
                eq(null),
                eq(null),
                eq(false)
        )).thenReturn(new LlmGateway.RoutedChatOnceResult(
                "{\"choices\":[{\"message\":{\"content\":\"[\\\"T1\\\"]\"}}]}",
                "prov-2",
                "model-2",
                null
        ));

        fixture.service.suggestTitles(fixture.baseRequest(), 99L);
        verify(fixture.configService, never()).recordHistory(any());
    }

    @Test
    void suggestTitles_shouldNotRecordHistoryWhenActorMissing() {
        TestFixture fixture = TestFixture.create();
        fixture.cfg.setHistoryEnabled(true);

        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"[\\\"T1\\\"]\"}}]}",
                        "prov-2",
                        "model-2",
                        null
                ));

        fixture.service.suggestTitles(fixture.baseRequest(), null);
        verify(fixture.configService, never()).recordHistory(any());
    }

    @Test
    void suggestTitles_shouldRenderPromptAndApplyContentTruncate() {
        TestFixture fixture = TestFixture.create();
        fixture.cfg.setMaxContentChars(5);

        AiPostTitleSuggestRequest req = fixture.baseRequest();
        req.setCount(null);
        req.setContent("   abcdefg   ");
        req.setBoardName("  BoardA ");
        req.setTags(Arrays.asList("  ", "java", null, " spring "));

        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"[\\\"T1\\\"]\"}}]}",
                        "p", "m", null
                ));

        fixture.service.suggestTitles(req, null);

        ArgumentCaptor<List> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(fixture.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TITLE_GEN),
                eq("pid-1"),
                eq("cfg-model"),
                messagesCaptor.capture(),
                eq(0.6),
                eq(0.7),
                eq(null),
                eq(null),
                eq(false)
        );

        @SuppressWarnings("unchecked")
        List<ChatMessage> messages = messagesCaptor.getValue();
        assertEquals(2, messages.size());
        String userPrompt = String.valueOf(messages.get(1).content());
        assertTrue(userPrompt.contains("count=3"));
        assertTrue(userPrompt.contains("版块：BoardA"));
        assertTrue(userPrompt.contains("标签：java、spring"));
        assertTrue(userPrompt.contains("内容=abcde"));
    }

    @Test
    void suggestTitles_shouldNotTruncateWhenMaxContentCharsNonPositive() {
        TestFixture fixture = TestFixture.create();
        fixture.cfg.setMaxContentChars(0);

        AiPostTitleSuggestRequest req = fixture.baseRequest();
        req.setContent("  abcdefg  ");

        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"[\\\"T1\\\"]\"}}]}",
                        "p", "m", null
                ));

        fixture.service.suggestTitles(req, null);

        ArgumentCaptor<List> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(fixture.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TITLE_GEN),
                eq("pid-1"),
                eq("cfg-model"),
                messagesCaptor.capture(),
                eq(0.6),
                eq(0.7),
                eq(null),
                eq(null),
                eq(false)
        );

        @SuppressWarnings("unchecked")
        List<ChatMessage> messages = messagesCaptor.getValue();
        String userPrompt = String.valueOf(messages.get(1).content());
        assertTrue(userPrompt.contains("内容=abcdefg"));
    }

    @Test
    void suggestTitles_shouldRenderEmptyPromptWhenTemplateIsBlankAndNullInputs() {
        TestFixture fixture = TestFixture.create();
        fixture.cfg.setHistoryEnabled(true);

        PromptsEntity blankTemplatePrompt = new PromptsEntity();
        blankTemplatePrompt.setPromptCode("TITLE_GEN");
        blankTemplatePrompt.setSystemPrompt("sys");
        blankTemplatePrompt.setUserPromptTemplate("   ");
        blankTemplatePrompt.setProviderId("pid-1");
        blankTemplatePrompt.setModelName("cfg-model");
        blankTemplatePrompt.setTemperature(0.6);
        blankTemplatePrompt.setTopP(0.7);
        when(fixture.promptsRepository.findByPromptCode("TITLE_GEN")).thenReturn(Optional.of(blankTemplatePrompt));

        AiPostTitleSuggestRequest req = fixture.baseRequest();
        req.setContent(null);
        req.setBoardName(null);
        req.setTags(null);

        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"[\\\"T1\\\"]\"}}]}",
                        "prov-2",
                        "model-2",
                        null
                ));

        fixture.service.suggestTitles(req, 7L);

        ArgumentCaptor<List> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(fixture.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TITLE_GEN),
                eq("pid-1"),
                eq("cfg-model"),
                messagesCaptor.capture(),
                eq(0.6),
                eq(0.7),
                eq(null),
                eq(null),
                eq(false)
        );
        @SuppressWarnings("unchecked")
        List<ChatMessage> messages = messagesCaptor.getValue();
        assertEquals("", String.valueOf(messages.get(1).content()));

        ArgumentCaptor<PostSuggestionGenHistoryEntity> historyCaptor = ArgumentCaptor.forClass(PostSuggestionGenHistoryEntity.class);
        verify(fixture.configService).recordHistory(historyCaptor.capture());
        PostSuggestionGenHistoryEntity history = historyCaptor.getValue();
        assertNull(history.getBoardName());
        assertEquals(List.of(), history.getInputTagsJson());
        assertEquals(0, history.getContentLen());
        assertNull(history.getContentExcerpt());
    }

    @Test
    void suggestTitles_shouldRenderEmptyPromptWhenTemplateIsNull() {
        TestFixture fixture = TestFixture.create();

        PromptsEntity nullTemplatePrompt = new PromptsEntity();
        nullTemplatePrompt.setPromptCode("TITLE_GEN");
        nullTemplatePrompt.setSystemPrompt("sys");
        nullTemplatePrompt.setUserPromptTemplate(null);
        nullTemplatePrompt.setProviderId("pid-1");
        nullTemplatePrompt.setModelName("cfg-model");
        nullTemplatePrompt.setTemperature(0.6);
        nullTemplatePrompt.setTopP(0.7);
        when(fixture.promptsRepository.findByPromptCode("TITLE_GEN")).thenReturn(Optional.of(nullTemplatePrompt));

        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"[\\\"T1\\\"]\"}}]}",
                        "p",
                        "m",
                        null
                ));

        fixture.service.suggestTitles(fixture.baseRequest(), null);

        ArgumentCaptor<List> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(fixture.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TITLE_GEN),
                eq("pid-1"),
                eq("cfg-model"),
                messagesCaptor.capture(),
                eq(0.6),
                eq(0.7),
                eq(null),
                eq(null),
                eq(false)
        );
        @SuppressWarnings("unchecked")
        List<ChatMessage> messages = messagesCaptor.getValue();
        assertEquals("", String.valueOf(messages.get(1).content()));
    }

    @Test
    void suggestTitles_shouldPassEnableThinkingToGatewayWhenTrue() {
        TestFixture fixture = TestFixture.create();

        PromptsEntity thinkingPrompt = new PromptsEntity();
        thinkingPrompt.setPromptCode("TITLE_GEN");
        thinkingPrompt.setSystemPrompt("sys");
        thinkingPrompt.setUserPromptTemplate("count={{count}}\n{{boardLine}}{{tagsLine}}内容={{content}}");
        thinkingPrompt.setProviderId("pid-1");
        thinkingPrompt.setModelName("cfg-model");
        thinkingPrompt.setTemperature(0.6);
        thinkingPrompt.setTopP(0.7);
        thinkingPrompt.setEnableDeepThinking(true);
        when(fixture.promptsRepository.findByPromptCode("TITLE_GEN")).thenReturn(Optional.of(thinkingPrompt));

        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"[\\\"T1\\\"]\"}}]}",
                        "p", "m", null
                ));

        fixture.service.suggestTitles(fixture.baseRequest(), null);

        verify(fixture.llmGateway).chatOnceRouted(
                eq(LlmQueueTaskType.TITLE_GEN),
                eq("pid-1"),
                eq("cfg-model"),
                any(),
                eq(0.6),
                eq(0.7),
                eq(null),
                eq(null),
                eq(true)
        );
    }

    @Test
    void suggestTitles_shouldSetHistoryBoardAndExcerptNullForBlankContent() {
        TestFixture fixture = TestFixture.create();
        fixture.cfg.setHistoryEnabled(true);

        AiPostTitleSuggestRequest req = fixture.baseRequest();
        req.setBoardName("  Tech  ");
        req.setContent("   ");

        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"[\\\"T1\\\"]\"}}]}",
                        "prov-2",
                        "model-2",
                        null
                ));

        fixture.service.suggestTitles(req, 7L);

        ArgumentCaptor<PostSuggestionGenHistoryEntity> historyCaptor = ArgumentCaptor.forClass(PostSuggestionGenHistoryEntity.class);
        verify(fixture.configService).recordHistory(historyCaptor.capture());
        PostSuggestionGenHistoryEntity history = historyCaptor.getValue();
        assertEquals("Tech", history.getBoardName());
        assertNull(history.getContentExcerpt());
    }

        @Test
        void suggestTitles_shouldTruncateHistoryExcerptTo240() {
                TestFixture fixture = TestFixture.create();
                fixture.cfg.setHistoryEnabled(true);

                AiPostTitleSuggestRequest req = fixture.baseRequest();
                req.setContent("X".repeat(500));

                when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                                                "{\"choices\":[{\"message\":{\"content\":\"[\\\"T1\\\"]\"}}]}",
                                                "prov-2",
                                                "model-2",
                                                null
                                ));

                fixture.service.suggestTitles(req, 7L);

                ArgumentCaptor<PostSuggestionGenHistoryEntity> historyCaptor = ArgumentCaptor.forClass(PostSuggestionGenHistoryEntity.class);
                verify(fixture.configService).recordHistory(historyCaptor.capture());
                assertEquals(240, historyCaptor.getValue().getContentExcerpt().length());
        }

    @Test
    void suggestTitles_shouldThrowWhenPromptMissing() {
        TestFixture fixture = TestFixture.create();
        when(fixture.promptsRepository.findByPromptCode(any())).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fixture.service.suggestTitles(fixture.baseRequest(), 1L));
        assertTrue(ex.getMessage().contains("Prompt code not found"));
    }

    @Test
    void suggestTitles_shouldWrapLlmException() {
        TestFixture fixture = TestFixture.create();
        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("timeout"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fixture.service.suggestTitles(fixture.baseRequest(), 1L));
        assertTrue(ex.getMessage().contains("上游AI调用失败"));
    }

    @Test
        void suggestTitles_shouldReturnEmptyTitlesWhenRoutedResultNull() {
        TestFixture fixture = TestFixture.create();
        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

                AiPostTitleSuggestResponse response = fixture.service.suggestTitles(fixture.baseRequest(), 1L);
                assertNotNull(response);
                assertTrue(response.getTitles().isEmpty());
                assertNull(response.getModel());
    }

    @Test
        void suggestTitles_shouldReturnEmptyTitlesWhenRoutedTextNull() {
        TestFixture fixture = TestFixture.create();
        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(null, "p", "m", null));

                AiPostTitleSuggestResponse response = fixture.service.suggestTitles(fixture.baseRequest(), 1L);
                assertNotNull(response);
                assertTrue(response.getTitles().isEmpty());
                assertEquals("m", response.getModel());
    }

    @Test
    void suggestTitles_shouldUseTextFallbackInExtractAssistantContent() {
        TestFixture fixture = TestFixture.create();
        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"text\":\"[\\\"TXT1\\\",\\\"TXT2\\\"]\"}]}",
                        "p", "m", null
                ));

        AiPostTitleSuggestResponse response = fixture.service.suggestTitles(fixture.baseRequest(), null);
        assertEquals(List.of("TXT1", "TXT2"), response.getTitles());
    }

    @Test
    void suggestTitles_shouldUseRawFallbackWhenNotOpenAiEnvelope() {
        TestFixture fixture = TestFixture.create();
        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "[\"R1\",\"R2\"]",
                        "p", "m", null
                ));

        AiPostTitleSuggestResponse response = fixture.service.suggestTitles(fixture.baseRequest(), null);
        assertEquals(List.of("R1", "R2"), response.getTitles());
    }

    @Test
    void suggestTitles_shouldFallbackRawWhenResponseIsInvalidJson() {
        TestFixture fixture = TestFixture.create();
        when(fixture.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "[\"A\",\"B\"] trailing-noise",
                        "p", "m", null
                ));

        AiPostTitleSuggestResponse response = fixture.service.suggestTitles(fixture.baseRequest(), null);
        assertEquals(List.of("A", "B"), response.getTitles());
    }

    @Test
    void suggestTitles_shouldFallbackRawWhenChoicesEmptyOrNonTextual() {
        TestFixture f1 = TestFixture.create();
        when(f1.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[]}",
                        "p", "m", null
                ));
        AiPostTitleSuggestResponse r1 = f1.service.suggestTitles(f1.baseRequest(), null);
        assertTrue(r1.getTitles().isEmpty());

        TestFixture f2 = TestFixture.create();
        when(f2.llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":{}},\"text\":123}]}",
                        "p", "m", null
                ));
        AiPostTitleSuggestResponse r2 = f2.service.suggestTitles(f2.baseRequest(), null);
        assertTrue(r2.getTitles().isEmpty());
    }

    @Test
    void parseTitlesFromAssistantText_shouldHandleNoiseDedupAndLimit() {
        TestFixture fixture = TestFixture.create();
        String longTitle = "L".repeat(80);
        List<String> titles = fixture.service.parseTitlesFromAssistantText(
                "prefix {\"titles\":[\" A \",\"A\",\"\\\"Q\\\"\",\"" + longTitle + "\",123]} suffix",
                2
        );

        assertEquals(2, titles.size());
        assertEquals("A", titles.get(0));
        assertEquals("Q", titles.get(1));
    }

    @Test
    void parseTitlesFromAssistantText_shouldThrowForInvalidJson() {
        TestFixture fixture = TestFixture.create();
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.parseTitlesFromAssistantText("not-json", 3));
    }

    @Test
    void parseTitlesFromAssistantText_shouldTreatNullAssistantTextAsInvalidJson() {
        TestFixture fixture = TestFixture.create();
        assertTrue(fixture.service.parseTitlesFromAssistantText(null, 3).isEmpty());
    }

        @Test
        void parseTitlesFromAssistantText_shouldReturnEmptyWhenObjectWithoutTitles() {
                TestFixture fixture = TestFixture.create();
                List<String> titles = fixture.service.parseTitlesFromAssistantText("{\"x\":1}", 3);
                assertTrue(titles.isEmpty());
        }

        @Test
        void parseTitlesFromAssistantText_shouldParseArrayWithNoiseAndFilterNonText() {
                TestFixture fixture = TestFixture.create();
                List<String> titles = fixture.service.parseTitlesFromAssistantText(
                                "prefix [\"A\", 100, \"B\", \"A\"] suffix",
                                10
                );
                assertEquals(List.of("A", "B"), titles);
        }

    @Test
    void parseTitlesFromAssistantText_shouldPreferObjectSliceWhenObjectAppearsBeforeArray() {
        TestFixture fixture = TestFixture.create();
        List<String> titles = fixture.service.parseTitlesFromAssistantText(
                "prefix {\"titles\":[\"A\"]} middle [\"B\"] suffix",
                10
        );
        assertEquals(List.of("A"), titles);
    }

        @Test
        void parseTitlesFromAssistantText_shouldReturnEmptyWhenExpectedCountZero() {
                TestFixture fixture = TestFixture.create();
                List<String> titles = fixture.service.parseTitlesFromAssistantText("[\"A\",\"B\"]", 0);
                assertTrue(titles.isEmpty());
        }

        @Test
        void parseTitlesFromAssistantText_shouldThrowWhenExpectedCountNegative() {
                TestFixture fixture = TestFixture.create();

                assertThrows(IllegalArgumentException.class,
                                () -> fixture.service.parseTitlesFromAssistantText("[\"A\",\"B\"]", -1));
        }

    private static final class TestFixture {
        private final PostTitleGenConfigService configService;
        private final LlmGateway llmGateway;
        private final PromptsRepository promptsRepository;
        private final PostSuggestionGenConfigEntity cfg;
        private final AiPostTitleService service;

        private TestFixture(PostTitleGenConfigService configService,
                            LlmGateway llmGateway,
                            PromptsRepository promptsRepository,
                            PostSuggestionGenConfigEntity cfg,
                            AiPostTitleService service) {
            this.configService = configService;
            this.llmGateway = llmGateway;
            this.promptsRepository = promptsRepository;
            this.cfg = cfg;
            this.service = service;
        }

        static TestFixture create() {
            PostTitleGenConfigService configService = mock(PostTitleGenConfigService.class);
            LlmGateway llmGateway = mock(LlmGateway.class);
            PromptsRepository promptsRepository = mock(PromptsRepository.class);
            AiPostTitleService service = new AiPostTitleService(configService, llmGateway, promptsRepository, mock(com.example.EnterpriseRagCommunity.repository.semantic.GenerationJobsRepository.class), new PromptLlmParamResolver());

            PostSuggestionGenConfigEntity cfg = new PostSuggestionGenConfigEntity();
            cfg.setEnabled(true);
            cfg.setDefaultCount(3);
            cfg.setMaxCount(10);
            cfg.setMaxContentChars(4000);
            cfg.setPromptCode("TITLE_GEN");
            cfg.setHistoryEnabled(false);
            cfg.setVersion(1);
            when(configService.getConfigEntityOrDefault()).thenReturn(cfg);

            PromptsEntity prompt = new PromptsEntity();
            prompt.setPromptCode("TITLE_GEN");
            prompt.setSystemPrompt("sys");
            prompt.setUserPromptTemplate("count={{count}}\n{{boardLine}}{{tagsLine}}内容={{content}}");
            prompt.setProviderId("pid-1");
            prompt.setModelName("cfg-model");
            prompt.setTemperature(0.6);
            prompt.setTopP(0.7);
            when(promptsRepository.findByPromptCode("TITLE_GEN")).thenReturn(Optional.of(prompt));

            return new TestFixture(configService, llmGateway, promptsRepository, cfg, service);
        }

        AiPostTitleSuggestRequest baseRequest() {
            AiPostTitleSuggestRequest req = new AiPostTitleSuggestRequest();
            req.setContent("hello world");
            req.setCount(2);
            req.setBoardName("General");
            req.setTags(List.of("java", "test"));
            return req;
        }
    }
}
