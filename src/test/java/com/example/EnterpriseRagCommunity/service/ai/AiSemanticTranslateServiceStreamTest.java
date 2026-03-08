package com.example.EnterpriseRagCommunity.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.SemanticTranslateHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AiSemanticTranslateServiceStreamTest {

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
    void translatePostStream_shouldCompleteWithError_whenTargetLangBlank() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("T", "C")));

        try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.translatePostStream(1L, "   ", 1L);
            assertThat(emitter).isSameAs(mocked.constructed().get(0));

            ArgumentCaptor<Throwable> err = ArgumentCaptor.forClass(Throwable.class);
            verify(emitter).completeWithError(err.capture());
            assertThat(err.getValue()).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void translateCommentStream_shouldThrow_whenCommentIdNull() {
        assertThatThrownBy(() -> service.translateCommentStream(null, "en", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("commentId");
    }

    @Test
    void translateCommentStream_shouldThrow_whenCommentMissing() {
        when(commentsRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.translateCommentStream(1L, "en", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("评论不存在");
    }

    @Test
    void translateCommentStream_shouldSendFinalDtoWithoutTitle_whenNoTitleContext() throws Exception {
        CommentsEntity comment = comment("正文");
        when(commentsRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());
        when(llmGateway.chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    OpenAiCompatClient.SseLineConsumer consumer = invocation.getArgument(8, OpenAiCompatClient.SseLineConsumer.class);
                    consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"Body only\"}}]}");
                    return new LlmGateway.RoutedChatStreamResult("P_STREAM", "M_STREAM", null);
                });

        CountDownLatch latch = new CountDownLatch(1);
        try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class, (emitter, context) -> {
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(emitter).complete();
        })) {
            SseEmitter emitter = service.translateCommentStream(1L, "en", 1L);
            assertThat(emitter).isSameAs(mocked.constructed().get(0));
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
            verify(emitter, times(2)).send(sent.capture());
            Object finalObj = sent.getAllValues().get(1);
            assertThat(finalObj).isInstanceOf(com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO.class);
            com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO dto =
                    (com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO) finalObj;
            assertThat(dto.getTranslatedTitle()).isNull();
            assertThat(dto.getTranslatedMarkdown()).isEqualTo("Body only");
        }
    }

    @Test
    void translatePostStream_shouldCompleteWithError_whenDisabled() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("T", "C")));
        SemanticTranslateConfigEntity cfg = enabledCfg();
        cfg.setEnabled(Boolean.FALSE);
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(cfg);

        try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.translatePostStream(1L, "en", 1L);
            assertThat(emitter).isSameAs(mocked.constructed().get(0));

            ArgumentCaptor<Throwable> err = ArgumentCaptor.forClass(Throwable.class);
            verify(emitter).completeWithError(err.capture());
            assertThat(err.getValue()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void translatePostStream_shouldThrow_whenPromptMissing() {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("T", "C")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.empty());

        try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
            assertThatThrownBy(() -> service.translatePostStream(1L, "en", 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Prompt code not found");
        }
    }

    @Test
    void translatePostStream_shouldSendFinalDtoAndComplete_whenCacheHit() throws Exception {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("T", "C")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));

        SemanticTranslateHistoryEntity h = new SemanticTranslateHistoryEntity();
        h.setTranslatedTitle("TT");
        h.setTranslatedMarkdown("MM");
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.of(h));

        try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.translatePostStream(1L, "en", 1L);
            verify(emitter).send(any(Object.class));
            verify(emitter).complete();
            verify(emitter, never()).completeWithError(any());
            verify(llmGateway, never()).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    void translatePostStream_shouldCompleteWithError_whenCacheHitButSendThrows() throws Exception {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("T", "C")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));

        SemanticTranslateHistoryEntity h = new SemanticTranslateHistoryEntity();
        h.setTranslatedTitle("TT");
        h.setTranslatedMarkdown("MM");
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.of(h));

        try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class, (emitter, context) -> {
            doThrow(new IOException("send fail")).when(emitter).send(any(Object.class));
        })) {
            SseEmitter emitter = service.translatePostStream(1L, "en", 1L);
            verify(emitter).completeWithError(any(IOException.class));
        }
    }

    @Test
    void translatePostStream_shouldStreamDeltasAndFinalDto_andRecordHistory_whenEnabled() throws Exception {
        PostsEntity p = post("x".repeat(130), "y".repeat(300));
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(p));

        SemanticTranslateConfigEntity cfg = enabledCfg();
        cfg.setHistoryEnabled(Boolean.TRUE);
        cfg.setVersion(7);
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(cfg);

        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());

        when(llmGateway.chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    OpenAiCompatClient.SseLineConsumer consumer = invocation.getArgument(8, OpenAiCompatClient.SseLineConsumer.class);
                    consumer.onLine("garbage");
                    consumer.onLine("data: [DONE]");
                    consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"# Hello\\n\"}}]}");
                    consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"World\"}}]}");
                    return new LlmGateway.RoutedChatStreamResult("P_STREAM", "M_STREAM", null);
                });

        CountDownLatch latch = new CountDownLatch(1);
        try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class, (emitter, context) -> {
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(emitter).complete();
        })) {
            SseEmitter emitter = service.translatePostStream(1L, "en", 99L);
            assertThat(emitter).isSameAs(mocked.constructed().get(0));

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
            verify(emitter, times(3)).send(sent.capture());
            verify(emitter).complete();
            verify(emitter, never()).completeWithError(any());

            List<Object> allSent = sent.getAllValues();
            assertThat(allSent.get(0)).isEqualTo(java.util.Collections.singletonMap("delta", "# Hello\n"));
            assertThat(allSent.get(1)).isEqualTo(java.util.Collections.singletonMap("delta", "World"));
            assertThat(allSent.get(2)).isInstanceOf(com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO.class);

            ArgumentCaptor<List<ChatMessage>> messagesCap = ArgumentCaptor.forClass(List.class);
            verify(llmGateway).chatStreamRouted(any(), any(), any(), messagesCap.capture(), any(), any(), any(), any(), any());
            assertThat(messagesCap.getValue()).hasSize(2);

            ArgumentCaptor<com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateHistoryEntity> historyCap =
                    ArgumentCaptor.forClass(com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateHistoryEntity.class);
            verify(semanticTranslateConfigService).recordHistory(historyCap.capture());
            assertThat(historyCap.getValue().getSourceTitleExcerpt()).hasSize(120);
            assertThat(historyCap.getValue().getSourceContentExcerpt()).hasSize(240);
            assertThat(historyCap.getValue().getTranslatedTitle()).isEqualTo("Hello");
            assertThat(historyCap.getValue().getTranslatedMarkdown()).isEqualTo("World");
        }
    }

    @Test
    void translatePostStream_shouldCompleteWithError_whenDeltaSendThrows() throws Exception {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("Title", "C")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());

        when(llmGateway.chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    OpenAiCompatClient.SseLineConsumer consumer = invocation.getArgument(8, OpenAiCompatClient.SseLineConsumer.class);
                    consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"X\"}}]}");
                    return new LlmGateway.RoutedChatStreamResult("P_STREAM", "M_STREAM", null);
                });

        CountDownLatch latch = new CountDownLatch(1);
        try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class, (emitter, context) -> {
            doThrow(new IOException("send fail")).when(emitter).send(any(Object.class));
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(emitter).completeWithError(any());
        })) {
            SseEmitter emitter = service.translatePostStream(1L, "en", 1L);
            assertThat(emitter).isSameAs(mocked.constructed().get(0));

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            verify(emitter).completeWithError(any());
        }
    }

    @Test
    void translatePostStream_shouldKeepTitleNull_whenNoLineBreakInStreamText() throws Exception {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("有标题", "正文")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());
        when(llmGateway.chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    OpenAiCompatClient.SseLineConsumer consumer = invocation.getArgument(8, OpenAiCompatClient.SseLineConsumer.class);
                    consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"单行正文\"}}]}");
                    return new LlmGateway.RoutedChatStreamResult("P_STREAM", "M_STREAM", null);
                });

        CountDownLatch latch = new CountDownLatch(1);
        try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class, (emitter, context) -> {
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(emitter).complete();
        })) {
            SseEmitter emitter = service.translatePostStream(1L, "en", 1L);
            assertThat(emitter).isSameAs(mocked.constructed().get(0));
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
            verify(emitter, times(2)).send(sent.capture());
            Object finalObj = sent.getAllValues().get(1);
            assertThat(finalObj).isInstanceOf(com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO.class);
            com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO dto =
                    (com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO) finalObj;
            assertThat(dto.getTranslatedTitle()).isNull();
            assertThat(dto.getTranslatedMarkdown()).isEqualTo("单行正文");
        }
    }

    @Test
    void translatePostStream_shouldExtractTitleWithoutHeadingPrefix_whenFirstLineHasNoHash() throws Exception {
        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(post("原标题", "正文")));
        when(semanticTranslateConfigService.getConfigEntityOrDefault()).thenReturn(enabledCfg());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(prompt("SYS", "{{content}}")));
        when(semanticTranslateHistoryRepository
                .findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(anyString(), anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Optional.empty());
        when(llmGateway.chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    OpenAiCompatClient.SseLineConsumer consumer = invocation.getArgument(8, OpenAiCompatClient.SseLineConsumer.class);
                    consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"Translated Title\\n\"}}]}");
                    consumer.onLine("data: {\"choices\":[{\"delta\":{\"content\":\"Translated Body\"}}]}");
                    return new LlmGateway.RoutedChatStreamResult("P_STREAM", "M_STREAM", null);
                });

        CountDownLatch latch = new CountDownLatch(1);
        try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class, (emitter, context) -> {
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(emitter).complete();
        })) {
            SseEmitter emitter = service.translatePostStream(1L, "en", 1L);
            assertThat(emitter).isSameAs(mocked.constructed().get(0));
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
            verify(emitter, times(3)).send(sent.capture());
            Object finalObj = sent.getAllValues().get(2);
            assertThat(finalObj).isInstanceOf(com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO.class);
            com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO dto =
                    (com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO) finalObj;
            assertThat(dto.getTranslatedTitle()).isEqualTo("Translated Title");
            assertThat(dto.getTranslatedMarkdown()).isEqualTo("Translated Body");
        }
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

    private static CommentsEntity comment(String content) {
        CommentsEntity c = new CommentsEntity();
        c.setIsDeleted(Boolean.FALSE);
        c.setContent(content);
        return c;
    }
}
