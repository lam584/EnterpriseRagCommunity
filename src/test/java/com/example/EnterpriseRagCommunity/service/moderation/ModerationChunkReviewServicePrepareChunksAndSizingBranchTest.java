package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServicePrepareChunksAndSizingBranchTest {

    @Test
    void prepareChunksIfNeeded_shouldReturnDisabledWhenConfigDisabled() {
        Fixture fx = new Fixture(cfg(false, 1000, null, null, null));
        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(11L, 101L));
        assertFalse(out.enabled);
        assertFalse(out.chunked);
        verify(fx.postsRepository, never()).findById(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldReturnNotChunkedWhenNoSourceExceedsThreshold() {
        Fixture fx = new Fixture(cfg(true, 1000, null, null, null));
        when(fx.postsRepository.findById(101L)).thenReturn(Optional.of(post(101L, repeat('a', 900), 900, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(11L, 101L));

        assertTrue(out.enabled);
        assertFalse(out.chunked);
        verify(fx.chunkSetRepository, never()).save(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldReturnCancelledWhenExistingSetCancelled() {
        Fixture fx = new Fixture(cfg(true, 1000, null, null, null));
        when(fx.postsRepository.findById(101L)).thenReturn(Optional.of(post(101L, repeat('a', 1500), 1500, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        ModerationChunkSetEntity cancelled = set(31L, ChunkSetStatus.CANCELLED, null);
        doReturn(cancelled).when(fx.service).ensureChunkSetForQueue(any());

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(11L, 101L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertTrue(out.cancelled);
        assertEquals(31L, out.chunkSetId);
    }

    @Test
    void prepareChunksIfNeeded_shouldReturnReadyWhenLockedSetAlreadyHasChunks() {
        Fixture fx = new Fixture(cfg(true, 1000, null, null, null));
        when(fx.postsRepository.findById(101L)).thenReturn(Optional.of(post(101L, repeat('a', 1500), 1500, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        ModerationChunkSetEntity existing = set(41L, ChunkSetStatus.RUNNING, null);
        ModerationChunkSetEntity locked = set(41L, ChunkSetStatus.RUNNING, 2);
        doReturn(existing).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(41L)).thenReturn(List.of(locked));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(11L, 101L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertFalse(out.cancelled);
        assertEquals(41L, out.chunkSetId);
        verify(fx.chunkRepository, never()).saveAll(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldCreatePostAndFileChunksAndAdjustOverlap() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 800, 20));
        when(fx.postsRepository.findById(101L)).thenReturn(Optional.of(post(101L, repeat('a', 1600), 1600, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(fx.llmConfigRepository.findAll()).thenReturn(List.of());

        ModerationChunkSetEntity running = set(51L, ChunkSetStatus.RUNNING, null);
        doReturn(running).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(51L)).thenReturn(List.of(running));

        PostAttachmentsEntity att = new PostAttachmentsEntity();
        att.setPostId(101L);
        att.setFileAssetId(700L);
        FileAssetsEntity file = new FileAssetsEntity();
        file.setOriginalName("a.txt");
        att.setFileAsset(file);
        when(fx.postAttachmentsRepository.findByPostId(eq(101L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(att)));

        FileAssetExtractionsEntity ext = new FileAssetExtractionsEntity();
        ext.setFileAssetId(700L);
        ext.setExtractStatus(FileAssetExtractionStatus.READY);
        ext.setExtractedText(repeat('b', 1300));
        when(fx.fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(ext));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(11L, 101L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertEquals(51L, out.chunkSetId);
        assertEquals(500, running.getChunkSizeChars());
        assertEquals(50, running.getOverlapChars());
        assertEquals(7, running.getTotalChunks());
        assertNotNull(running.getConfigJson());
        assertEquals(500, ((Number) running.getConfigJson().get("effectiveChunkSizeChars")).intValue());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModerationChunkEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fx.chunkRepository).saveAll(captor.capture());
        List<ModerationChunkEntity> chunks = captor.getValue();
        assertEquals(7, chunks.size());
        assertTrue(chunks.stream().anyMatch(c -> c.getSourceType() == ChunkSourceType.POST_TEXT));
        assertTrue(chunks.stream().anyMatch(c -> c.getSourceType() == ChunkSourceType.FILE_TEXT));
    }

    @Test
    void resolveChunkSizingDecision_shouldUseDefaultsWhenLlmConfigQueryFails() throws Exception {
        Fixture fx = new Fixture(cfg(true, null, null, null, null));
        when(fx.llmConfigRepository.findAll()).thenThrow(new RuntimeException("db"));

        Object decision = invokeResolveChunkSizingDecision(fx.service, cfg(true, null, null, null, null));
        Map<String, Object> log = budgetLog(decision);

        assertEquals(4000, effectiveChunkSizeChars(decision));
        assertFalse((Boolean) log.get("triggeredResharding"));
        assertEquals(50_000, ((Number) log.get("imageTokenBudget")).intValue());
        assertEquals(10, ((Number) log.get("maxImagesPerRequest")).intValue());
    }

    @Test
    void resolveChunkSizingDecision_shouldFallbackWhenPromptLookupFails() throws Exception {
        Fixture fx = new Fixture(cfg(true, null, 6000, null, null));
        ModerationLlmConfigEntity llm = new ModerationLlmConfigEntity();
        llm.setVisionPromptCode("VP");
        when(fx.llmConfigRepository.findAll()).thenReturn(List.of(llm));
        when(fx.promptsRepository.findByPromptCode("VP")).thenThrow(new RuntimeException("prompt"));

        Object decision = invokeResolveChunkSizingDecision(fx.service, cfg(true, null, 6000, null, null));
        Map<String, Object> log = budgetLog(decision);

        assertEquals(6000, effectiveChunkSizeChars(decision));
        assertFalse((Boolean) log.get("triggeredResharding"));
        verify(fx.promptsRepository).findByPromptCode("VP");
    }

    @Test
    void resolveChunkSizingDecision_shouldClampAndReshardWhenVisionBudgetTooTight() throws Exception {
        Fixture fx = new Fixture(cfg(true, null, 8000, null, null));
        ModerationLlmConfigEntity llm = new ModerationLlmConfigEntity();
        llm.setVisionPromptCode("VP");
        when(fx.llmConfigRepository.findAll()).thenReturn(List.of(llm));

        PromptsEntity prompt = new PromptsEntity();
        prompt.setVisionImageTokenBudget(0);
        prompt.setVisionMaxImagesPerRequest(500);
        prompt.setVisionHighResolutionImages(true);
        prompt.setVisionMaxPixels(-1);
        when(fx.promptsRepository.findByPromptCode("VP")).thenReturn(Optional.of(prompt));

        Object decision = invokeResolveChunkSizingDecision(fx.service, cfg(true, null, 8000, null, null));
        Map<String, Object> log = budgetLog(decision);

        assertEquals(512, effectiveChunkSizeChars(decision));
        assertTrue((Boolean) log.get("triggeredResharding"));
        assertEquals(1, ((Number) log.get("imageTokenBudget")).intValue());
        assertEquals(50, ((Number) log.get("maxImagesPerRequest")).intValue());
        assertEquals(16386, ((Number) log.get("perImageTokenEstimate")).intValue());
        assertTrue((Boolean) log.get("highResolutionImages"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rounds = (List<Map<String, Object>>) log.get("rounds");
        assertTrue(rounds.size() > 1);
    }

    private static ModerationChunkReviewConfigDTO cfg(Boolean enabled, Integer threshold, Integer chunkSize, Integer overlap, Integer maxChunks) {
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setEnabled(enabled);
        cfg.setChunkThresholdChars(threshold);
        cfg.setChunkSizeChars(chunkSize);
        cfg.setOverlapChars(overlap);
        cfg.setMaxChunksTotal(maxChunks);
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

    private static int effectiveChunkSizeChars(Object decision) throws Exception {
        Method m = decision.getClass().getDeclaredMethod("effectiveChunkSizeChars");
        m.setAccessible(true);
        return (Integer) m.invoke(decision);
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
