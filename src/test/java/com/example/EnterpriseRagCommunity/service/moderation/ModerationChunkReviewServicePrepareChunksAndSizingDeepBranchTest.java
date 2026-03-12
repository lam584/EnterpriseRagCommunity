package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServicePrepareChunksAndSizingDeepBranchTest {

    @Test
    void prepareChunksIfNeeded_shouldReturnDisabledWhenConfigIsNull() {
        Fixture fx = new Fixture(null);
        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(501L, 9001L));
        assertFalse(out.enabled);
        assertFalse(out.chunked);
    }

    @Test
    void prepareChunksIfNeeded_shouldReturnDisabledWhenEnabledIsNull() {
        Fixture fx = new Fixture(cfg(null, 1000, 500, 0, null, null));
        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(502L, 9002L));
        assertFalse(out.enabled);
        assertFalse(out.chunked);
    }

    @Test
    void prepareChunksIfNeeded_shouldNotChunkWhenPostAndFileAtThresholdBoundary() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, null, null));
        when(fx.postsRepository.findById(9003L)).thenReturn(Optional.of(post(9003L, repeat('p', 1000), 1000, false, false)));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(503L, 9003L));

        assertTrue(out.enabled);
        assertFalse(out.chunked);
        verify(fx.chunkSetRepository, never()).save(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldHandleLockedListNullAndContinueChunking() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, null, null));
        when(fx.postsRepository.findById(9004L)).thenReturn(Optional.of(post(9004L, repeat('a', 1400), 1400, false, false)));

        ModerationChunkSetEntity existing = set(904L, ChunkSetStatus.RUNNING, null);
        doReturn(existing).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(904L)).thenReturn(null);

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(504L, 9004L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertEquals(904L, out.chunkSetId);
        verify(fx.chunkRepository).saveAll(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldHandleLockedListEmptyAndContinueChunking() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, null, null));
        when(fx.postsRepository.findById(9005L)).thenReturn(Optional.of(post(9005L, repeat('a', 1400), 1400, false, false)));

        ModerationChunkSetEntity existing = set(905L, ChunkSetStatus.RUNNING, null);
        doReturn(existing).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(905L)).thenReturn(List.of());

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(505L, 9005L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertEquals(905L, out.chunkSetId);
        verify(fx.chunkRepository).saveAll(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldShortCircuitWhenChunkSetIsNull() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, null, null));
        when(fx.postsRepository.findById(9006L)).thenReturn(Optional.of(post(9006L, repeat('a', 1400), 1400, false, false)));
        doReturn(null).when(fx.service).ensureChunkSetForQueue(any());

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(506L, 9006L));

        assertTrue(out.enabled);
        assertFalse(out.chunked);
        verify(fx.chunkRepository, never()).saveAll(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldMixSemanticBoundaryAndHardCutInSemanticMode() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, 20, "SEMANTIC"));
        String text = repeat('a', 520) + "." + repeat('b', 620);
        when(fx.postsRepository.findById(9007L)).thenReturn(Optional.of(post(9007L, text, text.length(), false, false)));

        ModerationChunkSetEntity existing = set(907L, ChunkSetStatus.RUNNING, null);
        doReturn(existing).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(907L)).thenReturn(List.of(existing));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(507L, 9007L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModerationChunkEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fx.chunkRepository).saveAll(captor.capture());
        List<ModerationChunkEntity> chunks = captor.getValue();
        assertTrue(chunks.size() >= 3);
        assertTrue(chunks.stream().anyMatch(c -> c.getStartOffset() == 0 && c.getEndOffset() == 500));
        assertTrue(chunks.stream().anyMatch(c -> c.getStartOffset() == 500 && c.getEndOffset() == 521));
    }

    @Test
    void resolveChunkSizingDecision_shouldKeepVisionClampValuesAtExactBounds() throws Exception {
        Fixture fx = new Fixture(cfg(true, null, 4000, null, null, null));
        ModerationLlmConfigEntity llm = new ModerationLlmConfigEntity();
        llm.setMultimodalPromptCode("VP");
        when(fx.llmConfigRepository.findAll()).thenReturn(List.of(llm));

        PromptsEntity prompt = new PromptsEntity();
        prompt.setVisionImageTokenBudget(300_000);
        prompt.setVisionMaxImagesPerRequest(1);
        prompt.setVisionHighResolutionImages(false);
        prompt.setVisionMaxPixels(2_621_440);
        when(fx.promptsRepository.findByPromptCode("VP")).thenReturn(Optional.of(prompt));

        Object decision = invokeResolveChunkSizingDecision(fx.service, cfg(true, null, 4000, null, null, null));
        Map<String, Object> log = budgetLog(decision);

        assertEquals(300_000, ((Number) log.get("imageTokenBudget")).intValue());
        assertEquals(1, ((Number) log.get("maxImagesPerRequest")).intValue());
        assertFalse((Boolean) log.get("highResolutionImages"));
        assertEquals(2_621_440, ((Number) log.get("maxPixels")).intValue());
    }

    private static ModerationChunkReviewConfigDTO cfg(Boolean enabled,
                                                      Integer threshold,
                                                      Integer chunkSize,
                                                      Integer overlap,
                                                      Integer maxChunks,
                                                      String chunkMode) {
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setEnabled(enabled);
        cfg.setChunkThresholdChars(threshold);
        cfg.setChunkSizeChars(chunkSize);
        cfg.setOverlapChars(overlap);
        cfg.setMaxChunksTotal(maxChunks);
        cfg.setChunkMode(chunkMode);
        return cfg;
    }

    private static ModerationQueueEntity queue(Long id, Long contentId) {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(id);
        q.setContentType(ContentType.POST);
        q.setContentId(contentId);
        return q;
    }

    private static PostsEntity post(Long id, String content, Integer contentLength, Boolean chunkedReview, Boolean deleted) {
        PostsEntity p = new PostsEntity();
        p.setId(id);
        p.setContent(content);
        p.setContentLength(contentLength);
        p.setIsChunkedReview(chunkedReview);
        p.setIsDeleted(deleted);
        return p;
    }

    private static ModerationChunkSetEntity set(Long id, ChunkSetStatus status, Integer totalChunks) {
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(id);
        set.setStatus(status);
        set.setTotalChunks(totalChunks);
        return set;
    }

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(Math.max(0, n));
    }

    private static Object invokeResolveChunkSizingDecision(ModerationChunkReviewService service, ModerationChunkReviewConfigDTO cfg) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("resolveChunkSizingDecision", ModerationChunkReviewConfigDTO.class);
        m.setAccessible(true);
        return m.invoke(service, cfg);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> budgetLog(Object decision) throws Exception {
        Method m = decision.getClass().getDeclaredMethod("budgetConvergenceLog");
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(decision);
    }

    private static class Fixture {
        final ModerationChunkReviewConfigService configService = mock(ModerationChunkReviewConfigService.class);
        final ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        final ModerationLlmConfigRepository llmConfigRepository = mock(ModerationLlmConfigRepository.class);
        final PromptsRepository promptsRepository = mock(PromptsRepository.class);
        final ModerationChunkSetRepository chunkSetRepository = mock(ModerationChunkSetRepository.class);
        final ModerationChunkRepository chunkRepository = mock(ModerationChunkRepository.class);
        final PostsRepository postsRepository = mock(PostsRepository.class);
        final PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        final FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        final ModerationChunkReviewService service;

        Fixture(ModerationChunkReviewConfigDTO cfg) {
            when(configService.getConfig()).thenReturn(cfg);
            when(chunkSetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = spy(new ModerationChunkReviewService(
                    configService,
                    fallbackConfigRepository,
                    llmConfigRepository,
                    promptsRepository,
                    chunkSetRepository,
                    chunkRepository,
                    postsRepository,
                    postAttachmentsRepository,
                    fileAssetExtractionsRepository,
                    transactionManager
            ));
        }
    }
}
