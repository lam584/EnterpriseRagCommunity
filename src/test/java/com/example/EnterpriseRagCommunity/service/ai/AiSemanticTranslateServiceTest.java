package com.example.EnterpriseRagCommunity.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.SemanticTranslateHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;

class AiSemanticTranslateServiceTest {

    private SemanticTranslateConfigService semanticTranslateConfigService;
    private SemanticTranslateHistoryRepository semanticTranslateHistoryRepository;
    private PostsRepository postsRepository;
    private CommentsRepository commentsRepository;
    private LlmGateway llmGateway;
    private PromptsRepository promptsRepository;
    private PromptLlmParamResolver promptLlmParamResolver;

    private AiSemanticTranslateService service;

    @BeforeEach
    void setUp() {
        semanticTranslateConfigService = mock(SemanticTranslateConfigService.class);
        semanticTranslateHistoryRepository = mock(SemanticTranslateHistoryRepository.class);
        postsRepository = mock(PostsRepository.class);
        commentsRepository = mock(CommentsRepository.class);
        llmGateway = mock(LlmGateway.class);
        promptsRepository = mock(PromptsRepository.class);
        promptLlmParamResolver = new PromptLlmParamResolver();
        service = new AiSemanticTranslateService(
                semanticTranslateConfigService,
                semanticTranslateHistoryRepository,
                postsRepository,
                commentsRepository,
                llmGateway,
                promptsRepository,
                mock(com.example.EnterpriseRagCommunity.repository.semantic.GenerationJobsRepository.class),
                promptLlmParamResolver
        );
    }

    @Test
    void translatePost_shouldThrow_whenPostIdNull() {
        assertThatThrownBy(() -> service.translatePost(null, "en", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("postId");
    }

    @Test
    void translatePost_shouldThrow_whenPostMissing() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.translatePost(1L, "en", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("帖子不存在");
    }

    @Test
    void translateComment_shouldThrow_whenCommentIdNull() {
        assertThatThrownBy(() -> service.translateComment(null, "en", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("commentId");
    }

    @Test
    void translateComment_shouldThrow_whenCommentDeleted() {
        CommentsEntity c = new CommentsEntity();
        c.setIsDeleted(Boolean.TRUE);
        when(commentsRepository.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.translateComment(1L, "en", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("评论不存在");
    }

    @Test
    void translateComment_shouldTranslateAndRecordHistory_whenActorUserIdNull() {
        CommentsEntity c = new CommentsEntity();
        c.setIsDeleted(Boolean.FALSE);
        c.setContent(" comment-body ");
        when(commentsRepository.findById(1L)).thenReturn(Optional.of(c));

        SemanticTranslateConfigEntity cfg = enabledCfg();
        cfg.setHistoryEnabled(Boolean.TRUE);
        cfg.setVersion(5);
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(cfg);

        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());
        when(llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(openAiText("{\"markdown\":\"正文\"}"), "P_C", "M_C", null));

        SemanticTranslateResultDTO out = service.translateComment(1L, "en", null);
        assertThat(out.getTranslatedTitle()).isNull();
        assertThat(out.getTranslatedMarkdown()).isEqualTo("正文");

        ArgumentCaptor<SemanticTranslateHistoryEntity> cap = ArgumentCaptor.forClass(SemanticTranslateHistoryEntity.class);
        verify(semanticTranslateConfigService).recordHistory(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(0L);
    }

    @Test
    void translatePost_shouldThrow_whenTargetLangBlank() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("t", "c")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));

        assertThatThrownBy(() -> service.translatePost(1L, "   ", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetLang");
    }

    @Test
    void translatePost_shouldThrow_whenDisabled() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("t", "c")));
        SemanticTranslateConfigEntity cfg = enabledCfg();
        cfg.setEnabled(Boolean.FALSE);
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(cfg);

        assertThatThrownBy(() -> service.translatePost(1L, "en", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("翻译功能已关闭");
    }

    @Test
    void translatePost_shouldThrow_whenPromptMissing() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("t", "c")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.translatePost(1L, "en", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Prompt code not found");
    }

    @Test
    void translatePost_shouldUseDefaultPromptCode_whenCfgPromptBlank() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("t", "c")));
        SemanticTranslateConfigEntity cfg = enabledCfg();
        cfg.setPromptCode("   ");
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(cfg);

        when(promptsRepository.findByPromptCode(SemanticTranslateConfigService.DEFAULT_PROMPT_CODE))
                .thenReturn(Optional.of(prompt("SYS", "{{content}}")));

        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());

        when(llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(openAiMessageContent("{\"title\":\"t\",\"markdown\":\"m\"}"), "P1", "M1", null));

        SemanticTranslateResultDTO out = service.translatePost(1L, "en", 1L);
        assertThat(out.getTranslatedMarkdown()).isEqualTo("m");

        verify(promptsRepository).findByPromptCode(SemanticTranslateConfigService.DEFAULT_PROMPT_CODE);
    }

    @Test
    void translatePost_shouldShortCircuit_whenCacheHit() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("t", "c")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));

        SemanticTranslateHistoryEntity h = new SemanticTranslateHistoryEntity();
        h.setTranslatedTitle("TT");
        h.setTranslatedMarkdown("MM");
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.of(h));

        SemanticTranslateResultDTO out = service.translatePost(1L, "en", 1L);
        assertThat(out.getCached()).isTrue();
        assertThat(out.getTranslatedTitle()).isEqualTo("TT");
        assertThat(out.getTranslatedMarkdown()).isEqualTo("MM");
        assertThat(out.getModel()).isNull();
        assertThat(out.getLatencyMs()).isNull();

        verify(llmGateway, never()).chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(semanticTranslateConfigService, never()).recordHistory(any());
    }

    @Test
    void translatePost_shouldCallLlmWithTruncatedContent_whenMaxCharsEnabled() {
        PostsEntity p = post("title", "abcd");
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(p));

        SemanticTranslateConfigEntity cfg = enabledCfg();
        cfg.setMaxContentChars(3);
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(cfg);

        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());

        when(llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(openAiMessageContent("{\"title\":\"t\",\"markdown\":\"m\"}"), "P1", "M1", null));

        ArgumentCaptor<List<ChatMessage>> messagesCap = ArgumentCaptor.forClass(List.class);
        service.translatePost(1L, "en", 1L);

        verify(llmGateway).chatOnceRouted(any(), any(), any(), messagesCap.capture(), anyDouble(), anyDouble(), any(), any(), any());
        List<ChatMessage> messages = messagesCap.getValue();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).role()).isEqualTo("user");
        assertThat(messages.get(1).content()).isEqualTo("abc");
    }

    @Test
    void translatePost_shouldNotTruncate_whenMaxCharsIsZero() {
        PostsEntity p = post("title", "abcd");
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(p));

        SemanticTranslateConfigEntity cfg = enabledCfg();
        cfg.setMaxContentChars(0);
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(cfg);

        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());

        when(llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(openAiMessageContent("{\"title\":\"t\",\"markdown\":\"m\"}"), "P1", "M1", null));

        ArgumentCaptor<List<ChatMessage>> messagesCap = ArgumentCaptor.forClass(List.class);
        service.translatePost(1L, "en", 1L);

        verify(llmGateway).chatOnceRouted(any(), any(), any(), messagesCap.capture(), anyDouble(), anyDouble(), any(), any(), any());
        List<ChatMessage> messages = messagesCap.getValue();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).content()).isEqualTo("abcd");
    }

    @Test
    void translatePost_shouldThrowIllegalState_whenUpstreamThrows() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("t", "c")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());
        when(llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.translatePost(1L, "en", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("上游AI调用失败")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void translatePost_shouldParseJsonAssistantContent() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("t", "c")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());

        when(llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(openAiMessageContent("{\"title\":\"Hello\",\"markdown\":\"Body\"}"), "P1", "M1", null));

        SemanticTranslateResultDTO out = service.translatePost(1L, "en", 1L);
        assertThat(out.getCached()).isFalse();
        assertThat(out.getTranslatedTitle()).isEqualTo("Hello");
        assertThat(out.getTranslatedMarkdown()).isEqualTo("Body");
        assertThat(out.getModel()).isEqualTo("M1");
    }

    @Test
    void translatePost_shouldFallbackToAssistantText_whenNotJson() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("t", "c")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());

        when(llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(openAiMessageContent("  plain text  "), "P1", "M1", null));

        SemanticTranslateResultDTO out = service.translatePost(1L, "en", 1L);
        assertThat(out.getTranslatedTitle()).isNull();
        assertThat(out.getTranslatedMarkdown()).isEqualTo("plain text");
    }

    @Test
    void translatePost_shouldFallbackToAssistantRawText_whenParsedMarkdownBlank() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("t", "c")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());

        String assistantJson = "{\"title\":\"Hello\",\"markdown\":\"   \"}";
        when(llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(openAiMessageContent(assistantJson), "P1", "M1", null));

        SemanticTranslateResultDTO out = service.translatePost(1L, "en", 1L);
        assertThat(out.getTranslatedTitle()).isEqualTo("Hello");
        assertThat(out.getTranslatedMarkdown()).isEqualTo(assistantJson);
    }

    @Test
    void translatePost_shouldReturnEmptyMarkdown_whenRoutedResultNull() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("t", "c")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());
        when(llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        SemanticTranslateResultDTO out = service.translatePost(1L, "en", 1L);
        assertThat(out.getTranslatedTitle()).isNull();
        assertThat(out.getTranslatedMarkdown()).isEqualTo("");
        assertThat(out.getModel()).isNull();
    }

    @Test
    void translatePost_shouldRecordHistory_whenHistoryEnabled() {
        PostsEntity p = post("x".repeat(130), "y".repeat(300));
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(p));

        SemanticTranslateConfigEntity cfg = enabledCfg();
        cfg.setHistoryEnabled(Boolean.TRUE);
        cfg.setVersion(3);
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(cfg);

        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());

        when(llmGateway.chatOnceRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(openAiText("{\"title\":\"T\",\"markdown\":\"M\"}"), "P_USED", "M_USED", null));

        service.translatePost(1L, "en", 99L);

        ArgumentCaptor<SemanticTranslateHistoryEntity> cap = ArgumentCaptor.forClass(SemanticTranslateHistoryEntity.class);
        verify(semanticTranslateConfigService).recordHistory(cap.capture());
        SemanticTranslateHistoryEntity h = cap.getValue();
        assertThat(h.getUserId()).isEqualTo(99L);
        assertThat(h.getSourceTitleExcerpt()).hasSize(120);
        assertThat(h.getSourceContentExcerpt()).hasSize(240);
    }

    private static SemanticTranslateConfigEntity enabledCfg() {
        SemanticTranslateConfigEntity cfg = new SemanticTranslateConfigEntity();
        cfg.setEnabled(Boolean.TRUE);
        cfg.setPromptCode(SemanticTranslateConfigService.DEFAULT_PROMPT_CODE);
        cfg.setMaxContentChars(99999);
        cfg.setHistoryEnabled(Boolean.FALSE);
        return cfg;
    }

    private static PromptsEntity prompt(String system, String userTemplate) {
        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt(system);
        p.setUserPromptTemplate(userTemplate);
        return p;
    }

    private static PostsEntity post(String title, String content) {
        PostsEntity p = new PostsEntity();
        p.setTitle(title);
        p.setContent(content);
        p.setIsDeleted(Boolean.FALSE);
        return p;
    }

    private static String openAiMessageContent(String assistantContent) {
        String safe = assistantContent == null ? "" : assistantContent;
        return "{\"choices\":[{\"message\":{\"content\":" + jsonString(safe) + "}}]}";
    }

    private static String openAiText(String assistantContent) {
        String safe = assistantContent == null ? "" : assistantContent;
        return "{\"choices\":[{\"text\":" + jsonString(safe) + "}]}";
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
