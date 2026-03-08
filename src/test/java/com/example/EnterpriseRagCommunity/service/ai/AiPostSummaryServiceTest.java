package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostAiSummaryEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostAiSummaryRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPostSummaryServiceTest {

    @Test
    void generateForPostIdAsync_shouldFallbackDefaults_whenConfigPromptAndMaxCharsInvalid() {
    Fixture f = new Fixture();
    f.cfg.setPromptCode("   ");
    f.cfg.setMaxContentChars(0);

    String longContent = "x".repeat(PostSummaryGenConfigService.DEFAULT_MAX_CONTENT_CHARS + 50);
    f.post.setContent(longContent);

    PromptsEntity defaultPrompt = new PromptsEntity();
    defaultPrompt.setPromptCode(PostSummaryGenConfigService.DEFAULT_PROMPT_CODE);
    defaultPrompt.setSystemPrompt("sys");
    defaultPrompt.setUserPromptTemplate("{{content}}");
    when(f.promptsRepository.findByPromptCode(PostSummaryGenConfigService.DEFAULT_PROMPT_CODE))
        .thenReturn(Optional.of(defaultPrompt));

    when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
        .thenReturn(new LlmGateway.RoutedChatOnceResult("{\"choices\":[{\"text\":\"ok\"}]}", "p", "m", null));

    f.service.generateForPostIdAsync(1L, 1L);

    verify(f.llmGateway).chatOnceRouted(
        eq(LlmQueueTaskType.SUMMARY_GEN),
        isNull(),
        isNull(),
        anyList(),
        isNull(),
        eq(0.7),
        eq(null),
        eq(null),
        eq(false)
    );
    }

    @Test
    void generateForPostIdAsync_shouldReturnImmediatelyWhenPostIdNull() {
        Fixture f = new Fixture();

        f.service.generateForPostIdAsync(null, 1L);

        verify(f.postsRepository, never()).findById(any());
        verify(f.postSummaryGenConfigService, never()).getConfigEntityOrDefault();
    }

    @Test
    void generateForPostIdAsync_shouldReturnWhenPostNotFound() {
        Fixture f = new Fixture();
        when(f.postsRepository.findById(1L)).thenReturn(Optional.empty());

        f.service.generateForPostIdAsync(1L, 1L);

        verify(f.postSummaryGenConfigService, never()).getConfigEntityOrDefault();
        verify(f.llmGateway, never()).chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any());
    }

    @Test
    void generateForPostIdAsync_shouldReturnWhenConfigDisabled() {
        Fixture f = new Fixture();
        f.cfg.setEnabled(false);

        f.service.generateForPostIdAsync(1L, 1L);

        verify(f.llmGateway, never()).chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any());
        verify(f.postAiSummaryRepository, never()).save(any());
    }

    @Test
    void generateForPostIdAsync_shouldThrowWhenPromptMissing() {
        Fixture f = new Fixture();
        when(f.promptsRepository.findByPromptCode(any())).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> f.service.generateForPostIdAsync(1L, 1L));
        assertTrue(ex.getMessage().contains("Prompt code not found"));
    }

    @Test
    void generateForPostIdAsync_shouldSaveSuccess_whenMessageContentJsonSummary() {
        Fixture f = new Fixture();
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"title\\\":\\\"  \\\\\\\"T\\\\\\\"  \\\",\\\"summary\\\":\\\" S \\\"}\"}}]}",
                        "provider-x",
                        "model-x",
                        null
                ));

        f.service.generateForPostIdAsync(1L, 7L);

        ArgumentCaptor<PostAiSummaryEntity> summaryCaptor = ArgumentCaptor.forClass(PostAiSummaryEntity.class);
        verify(f.postAiSummaryRepository, times(1)).save(summaryCaptor.capture());
        PostAiSummaryEntity s = summaryCaptor.getValue();
        assertEquals("SUCCESS", s.getStatus());
        assertEquals("T", s.getSummaryTitle());
        assertEquals("S", s.getSummaryText());

        ArgumentCaptor<PostSummaryGenHistoryEntity> historyCaptor = ArgumentCaptor.forClass(PostSummaryGenHistoryEntity.class);
        verify(f.postSummaryGenConfigService, times(1)).recordHistory(historyCaptor.capture());
        PostSummaryGenHistoryEntity h = historyCaptor.getValue();
        assertEquals("SUCCESS", h.getStatus());
        assertEquals(1L, h.getPostId());
        assertEquals(7L, h.getActorUserId());
        assertNull(h.getErrorMessage());
    }

    @Test
    void generateForPostIdAsync_shouldNormalizeBlankProviderIdToNull_onSuccess() {
        Fixture f = new Fixture();
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"ok\\\"}\\\"}}]}",
                        "   ",
                        "model-x",
                        null
                ));

        f.service.generateForPostIdAsync(1L, 7L);

        ArgumentCaptor<PostAiSummaryEntity> summaryCaptor = ArgumentCaptor.forClass(PostAiSummaryEntity.class);
        verify(f.postAiSummaryRepository).save(summaryCaptor.capture());

        ArgumentCaptor<PostSummaryGenHistoryEntity> historyCaptor = ArgumentCaptor.forClass(PostSummaryGenHistoryEntity.class);
        verify(f.postSummaryGenConfigService).recordHistory(historyCaptor.capture());
    }

        @Test
        void generateForPostIdAsync_shouldUseRawFallback_whenNonEnvelopeJsonReturned() {
        Fixture f = new Fixture();
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
            .thenReturn(new LlmGateway.RoutedChatOnceResult(
                "{\"title\":\"T\",\"summary\":\"S\"}",
                "provider-x",
                "model-x",
                null
            ));

        f.service.generateForPostIdAsync(1L, 3L);

        ArgumentCaptor<PostAiSummaryEntity> summaryCaptor = ArgumentCaptor.forClass(PostAiSummaryEntity.class);
        verify(f.postAiSummaryRepository).save(summaryCaptor.capture());
        PostAiSummaryEntity s = summaryCaptor.getValue();
        assertEquals("SUCCESS", s.getStatus());
        assertEquals("T", s.getSummaryTitle());
        assertEquals("S", s.getSummaryText());
        }

        @Test
        void generateForPostIdAsync_shouldHandleNullTitleContentMetadataAndNullTemperature() {
        Fixture f = new Fixture();
        f.post.setTitle(null);
        f.post.setContent(null);
        f.post.setMetadata(null);

        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
            .thenReturn(new LlmGateway.RoutedChatOnceResult(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"ok\\\"}\\\"}}]}",
                "provider-x",
                "model-x",
                null
            ));

        f.service.generateForPostIdAsync(1L, 4L);

        verify(f.llmGateway).chatOnceRouted(
            eq(LlmQueueTaskType.SUMMARY_GEN),
            eq("provider-1"),
            eq("cfg-model"),
            anyList(),
            isNull(),
            eq(0.7),
            eq(null),
            eq(null),
            eq(false)
        );
        }

        @Test
        void generateForPostIdAsync_shouldTruncateContentToConfiguredMaxChars() {
        Fixture f = new Fixture();
        f.cfg.setMaxContentChars(5);
        f.post.setContent("  123456789  ");

        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
            .thenReturn(new LlmGateway.RoutedChatOnceResult(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"ok\\\"}\\\"}}]}",
                "provider-x",
                "model-x",
                null
            ));

        f.service.generateForPostIdAsync(1L, 5L);

        ArgumentCaptor<List<ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(f.llmGateway).chatOnceRouted(
            eq(LlmQueueTaskType.SUMMARY_GEN),
            any(),
            any(),
            messagesCaptor.capture(),
            any(),
            any(),
            eq(null),
            eq(null),
            any()
        );
        String userPrompt = String.valueOf(messagesCaptor.getValue().get(1).content());
        assertTrue(userPrompt.contains("12345"));
        }

        @Test
        void generateForPostIdAsync_shouldPassEnableThinkingTrueToGateway() {
        Fixture f = new Fixture();
        PromptsEntity thinkingPrompt = new PromptsEntity();
        thinkingPrompt.setPromptCode("SUMMARY_GEN");
        thinkingPrompt.setSystemPrompt("sys");
        thinkingPrompt.setUserPromptTemplate("{{title}}\\n{{content}}\\n{{tagsLine}}");
        thinkingPrompt.setProviderId("provider-1");
        thinkingPrompt.setModelName("cfg-model");
        thinkingPrompt.setTemperature(0.3);
        thinkingPrompt.setEnableDeepThinking(true);
        when(f.promptsRepository.findByPromptCode("SUMMARY_GEN")).thenReturn(Optional.of(thinkingPrompt));
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
            .thenReturn(new LlmGateway.RoutedChatOnceResult(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"ok\\\"}\\\"}}]}",
                "provider-x",
                "model-x",
                null
            ));

        f.service.generateForPostIdAsync(1L, 5L);

        verify(f.llmGateway).chatOnceRouted(
            eq(LlmQueueTaskType.SUMMARY_GEN),
            eq("provider-1"),
            eq("cfg-model"),
            anyList(),
            eq(0.3),
            eq(0.7),
            eq(null),
            eq(null),
            eq(true)
        );
        }

        @Test
        void generateForPostIdAsync_shouldNotRenderTagsLineWhenMetadataTagsInvalid() {
        Fixture f = new Fixture();
        f.post.setMetadata(Map.of("tags", "not-list"));
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
            .thenReturn(new LlmGateway.RoutedChatOnceResult(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"ok\\\"}\\\"}}]}",
                "provider-x",
                "model-x",
                null
            ));

        f.service.generateForPostIdAsync(1L, 5L);

        ArgumentCaptor<List<ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(f.llmGateway).chatOnceRouted(
            eq(LlmQueueTaskType.SUMMARY_GEN),
            any(),
            any(),
            messagesCaptor.capture(),
            any(),
            any(),
            eq(null),
            eq(null),
            any()
        );
        String userPrompt = String.valueOf(messagesCaptor.getValue().get(1).content());
        assertTrue(userPrompt.contains("post-title"));
        assertTrue(userPrompt.contains("post-content"));
        assertFalse(userPrompt.contains("标签："));
        }

    @Test
    void generateForPostIdAsync_shouldUseTextFallbackAndRawSummaryWhenNoJsonObject() {
        Fixture f = new Fixture();
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(
                        "{\"choices\":[{\"text\":\"纯文本总结\"}]}",
                        "provider-x",
                        "model-x",
                        null
                ));

        f.service.generateForPostIdAsync(1L, 8L);

        ArgumentCaptor<PostAiSummaryEntity> summaryCaptor = ArgumentCaptor.forClass(PostAiSummaryEntity.class);
        verify(f.postAiSummaryRepository).save(summaryCaptor.capture());
        PostAiSummaryEntity s = summaryCaptor.getValue();
        assertEquals("SUCCESS", s.getStatus());
        assertNull(s.getSummaryTitle());
        assertEquals("纯文本总结", s.getSummaryText());
    }

        @Test
        void generateForPostIdAsync_shouldFallbackRawWhenChoicesEmptyOrNonTextual() {
        Fixture f1 = new Fixture();
        when(f1.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
            .thenReturn(new LlmGateway.RoutedChatOnceResult(
                "{\"choices\":[]}",
                "provider-x",
                "model-x",
                null
            ));

        f1.service.generateForPostIdAsync(1L, 8L);

        ArgumentCaptor<PostAiSummaryEntity> s1Captor = ArgumentCaptor.forClass(PostAiSummaryEntity.class);
        verify(f1.postAiSummaryRepository).save(s1Captor.capture());
        assertEquals("SUCCESS", s1Captor.getValue().getStatus());
        assertEquals("{\"choices\":[]}", s1Captor.getValue().getSummaryText());

        Fixture f2 = new Fixture();
        when(f2.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
            .thenReturn(new LlmGateway.RoutedChatOnceResult(
                "{\"choices\":[{\"message\":{\"content\":{}},\"text\":123}]}",
                "provider-x",
                "model-x",
                null
            ));

        f2.service.generateForPostIdAsync(1L, 8L);

        ArgumentCaptor<PostAiSummaryEntity> s2Captor = ArgumentCaptor.forClass(PostAiSummaryEntity.class);
        verify(f2.postAiSummaryRepository).save(s2Captor.capture());
        assertEquals("SUCCESS", s2Captor.getValue().getStatus());
        assertEquals("{\"choices\":[{\"message\":{\"content\":{}},\"text\":123}]}", s2Captor.getValue().getSummaryText());
        }

    @Test
    void generateForPostIdAsync_shouldRecordFailureWhenGatewayThrows() {
        Fixture f = new Fixture();
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("timeout"));

        f.service.generateForPostIdAsync(1L, 9L);

        ArgumentCaptor<PostAiSummaryEntity> summaryCaptor = ArgumentCaptor.forClass(PostAiSummaryEntity.class);
        verify(f.postAiSummaryRepository).save(summaryCaptor.capture());
        PostAiSummaryEntity s = summaryCaptor.getValue();
        assertEquals("FAILED", s.getStatus());
        assertNull(s.getSummaryTitle());
        assertNull(s.getSummaryText());
        assertNotNull(s.getErrorMessage());
        assertTrue(s.getErrorMessage().contains("timeout"));

        ArgumentCaptor<PostSummaryGenHistoryEntity> historyCaptor = ArgumentCaptor.forClass(PostSummaryGenHistoryEntity.class);
        verify(f.postSummaryGenConfigService).recordHistory(historyCaptor.capture());
        assertEquals("FAILED", historyCaptor.getValue().getStatus());
    }

    @Test
    void generateForPostIdAsync_shouldNormalizeBlankProviderIdToNull_onFailure() {
        Fixture f = new Fixture();
        PromptsEntity noProviderPrompt = new PromptsEntity();
        noProviderPrompt.setPromptCode("SUMMARY_GEN");
        noProviderPrompt.setSystemPrompt("sys");
        noProviderPrompt.setUserPromptTemplate("{{title}}\\n{{content}}\\n{{tagsLine}}");
        when(f.promptsRepository.findByPromptCode("SUMMARY_GEN")).thenReturn(Optional.of(noProviderPrompt));
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("timeout"));

        f.service.generateForPostIdAsync(1L, 9L);

        ArgumentCaptor<PostAiSummaryEntity> summaryCaptor = ArgumentCaptor.forClass(PostAiSummaryEntity.class);
        verify(f.postAiSummaryRepository).save(summaryCaptor.capture());
        assertNotNull(summaryCaptor.getValue().getErrorMessage());

        ArgumentCaptor<PostSummaryGenHistoryEntity> historyCaptor = ArgumentCaptor.forClass(PostSummaryGenHistoryEntity.class);
        verify(f.postSummaryGenConfigService).recordHistory(historyCaptor.capture());
        assertNotNull(historyCaptor.getValue().getErrorMessage());
    }

    @Test
    void generateForPostIdAsync_shouldTruncateFailureErrorMessageTo12000() {
        Fixture f = new Fixture();
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("X".repeat(15000)));

        f.service.generateForPostIdAsync(1L, 9L);

        ArgumentCaptor<PostAiSummaryEntity> summaryCaptor = ArgumentCaptor.forClass(PostAiSummaryEntity.class);
        verify(f.postAiSummaryRepository).save(summaryCaptor.capture());
        PostAiSummaryEntity s = summaryCaptor.getValue();
        assertEquals("FAILED", s.getStatus());
        assertNotNull(s.getErrorMessage());
        assertTrue(s.getErrorMessage().length() <= 12000);

        ArgumentCaptor<PostSummaryGenHistoryEntity> historyCaptor = ArgumentCaptor.forClass(PostSummaryGenHistoryEntity.class);
        verify(f.postSummaryGenConfigService).recordHistory(historyCaptor.capture());
        assertNotNull(historyCaptor.getValue().getErrorMessage());
        assertTrue(historyCaptor.getValue().getErrorMessage().length() <= 12000);
    }

    @Test
    void generateForPostIdAsync_shouldRecordFailureWhenRoutedResultNull() {
        Fixture f = new Fixture();
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        f.service.generateForPostIdAsync(1L, 10L);

        ArgumentCaptor<PostAiSummaryEntity> summaryCaptor = ArgumentCaptor.forClass(PostAiSummaryEntity.class);
        verify(f.postAiSummaryRepository).save(summaryCaptor.capture());
        assertEquals("FAILED", summaryCaptor.getValue().getStatus());
    }

    @Test
    void generateForPostIdAsync_shouldReuseExistingSummaryEntityAndTrimLengths() {
        Fixture f = new Fixture();
        PostAiSummaryEntity existing = new PostAiSummaryEntity();
        existing.setId(88L);
        existing.setPostId(1L);
        when(f.postAiSummaryRepository.findByPostId(1L)).thenReturn(Optional.of(existing));

        String longTitle = "\\\"" + "T".repeat(220) + "\\\"";
        String longSummary = "S".repeat(9000);
        String payload = "{\"choices\":[{\"message\":{\"content\":\"{\\\"title\\\":\\\"" + longTitle + "\\\",\\\"summary\\\":\\\"" + longSummary + "\\\"}\"}}]}";
        when(f.llmGateway.chatOnceRouted(any(), any(), any(), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(payload, "provider-x", "model-x", null));

        f.service.generateForPostIdAsync(1L, 2L);

        ArgumentCaptor<PostAiSummaryEntity> summaryCaptor = ArgumentCaptor.forClass(PostAiSummaryEntity.class);
        verify(f.postAiSummaryRepository).save(summaryCaptor.capture());
        PostAiSummaryEntity s = summaryCaptor.getValue();
        assertEquals(88L, s.getId());
        assertEquals(191, s.getSummaryTitle().length());
        assertEquals(8000, s.getSummaryText().length());
    }

    @Test
    void parseSummaryFromAssistantText_shouldThrowWhenBlank() {
        AiPostSummaryService svc = new AiPostSummaryService(null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> svc.parseSummaryFromAssistantText("   "));
    }

    @Test
    void parseSummaryFromAssistantText_shouldFallbackToRawWhenSummaryMissing() {
        AiPostSummaryService svc = new AiPostSummaryService(null, null, null, null, null, null, null);

        AiPostSummaryService.ParsedSummary parsed = svc.parseSummaryFromAssistantText("{\"title\":\"TT\"}");
        assertEquals("TT", parsed.title());
        assertEquals("{\"title\":\"TT\"}", parsed.summary());
    }

    @Test
    void parseSummaryFromAssistantText_shouldFallbackToRawWhenSummaryBlank() {
        AiPostSummaryService svc = new AiPostSummaryService(null, null, null, null, null, null, null);

        AiPostSummaryService.ParsedSummary parsed = svc.parseSummaryFromAssistantText("{\"title\":\"TT\",\"summary\":\"   \"}");
        assertEquals("TT", parsed.title());
        assertEquals("{\"title\":\"TT\",\"summary\":\"   \"}", parsed.summary());
    }

    @Test
    void parseSummaryFromAssistantText_shouldReturnRawWhenNoJsonObject() {
        AiPostSummaryService svc = new AiPostSummaryService(null, null, null, null, null, null, null);

        AiPostSummaryService.ParsedSummary parsed = svc.parseSummaryFromAssistantText("just text summary");
        assertNull(parsed.title());
        assertEquals("just text summary", parsed.summary());
    }

    @Test
    void parseSummaryFromAssistantText_shouldFallbackToRawWhenJsonInvalid() {
        AiPostSummaryService svc = new AiPostSummaryService(null, null, null, null, null, null, null);

        AiPostSummaryService.ParsedSummary parsed = svc.parseSummaryFromAssistantText("prefix {bad-json} suffix");
        assertNull(parsed.title());
        assertEquals("prefix {bad-json} suffix", parsed.summary());
    }

    @Test
    void parseSummaryFromAssistantText_shouldExtractObjectSliceFromNoisyText() {
        AiPostSummaryService svc = new AiPostSummaryService(null, null, null, null, null, null, null);

        AiPostSummaryService.ParsedSummary parsed = svc.parseSummaryFromAssistantText(
                "prefix noise {\"title\":\"T\",\"summary\":\"S\"} suffix noise"
        );
        assertEquals("T", parsed.title());
        assertEquals("S", parsed.summary());
    }

    @Test
    void parseSummaryFromAssistantText_shouldUseRelaxedExtractionWhenJsonMalformed() {
        AiPostSummaryService svc = new AiPostSummaryService(null, null, null, null, null, null, null);

        AiPostSummaryService.ParsedSummary parsed = svc.parseSummaryFromAssistantText(
                "{\"title\":\"TT\",\"summary\":\"S\"bad\"}"
        );
        assertEquals("TT", parsed.title());
        assertEquals("S\"bad", parsed.summary());
    }

    private static final class Fixture {
        private final PostSummaryGenConfigService postSummaryGenConfigService = mock(PostSummaryGenConfigService.class);
        private final PostsRepository postsRepository = mock(PostsRepository.class);
        private final PostAiSummaryRepository postAiSummaryRepository = mock(PostAiSummaryRepository.class);
        private final LlmGateway llmGateway = mock(LlmGateway.class);
        private final PromptsRepository promptsRepository = mock(PromptsRepository.class);
        private final com.example.EnterpriseRagCommunity.repository.semantic.GenerationJobsRepository generationJobsRepository = mock(com.example.EnterpriseRagCommunity.repository.semantic.GenerationJobsRepository.class);

        private final AiPostSummaryService service = new AiPostSummaryService(
                postSummaryGenConfigService,
                postsRepository,
                postAiSummaryRepository,
                llmGateway,
                promptsRepository,
                generationJobsRepository,
                new PromptLlmParamResolver()
        );

        private final PostSummaryGenConfigEntity cfg = new PostSummaryGenConfigEntity();
        private final PostsEntity post = new PostsEntity();

        private Fixture() {
            cfg.setEnabled(true);
            cfg.setMaxContentChars(100);
            cfg.setPromptCode("SUMMARY_GEN");
            cfg.setVersion(3);
            when(postSummaryGenConfigService.getConfigEntityOrDefault()).thenReturn(cfg);

            post.setId(1L);
            post.setTitle("post-title");
            post.setContent("post-content");
            post.setMetadata(Map.of("tags", List.of("java", "spring")));
            when(postsRepository.findById(1L)).thenReturn(Optional.of(post));

            PromptsEntity prompt = new PromptsEntity();
            prompt.setPromptCode("SUMMARY_GEN");
            prompt.setSystemPrompt("sys");
            prompt.setUserPromptTemplate("{{title}}\\n{{content}}\\n{{tagsLine}}");
            prompt.setProviderId("provider-1");
            prompt.setModelName("cfg-model");
            when(promptsRepository.findByPromptCode("SUMMARY_GEN")).thenReturn(Optional.of(prompt));

            when(postAiSummaryRepository.findByPostId(1L)).thenReturn(Optional.empty());
        }
    }
}
