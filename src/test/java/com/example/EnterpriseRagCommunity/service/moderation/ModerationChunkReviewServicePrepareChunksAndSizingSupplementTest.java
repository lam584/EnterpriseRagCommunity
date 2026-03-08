package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServicePrepareChunksAndSizingSupplementTest {

    @Test
    void resolveChunkSizingDecision_shouldUseDefaultWhenConfigIsNull() throws Exception {
        Fixture fx = new Fixture(cfg(true, null, null, null, null, null));

        Object decision = invokeResolveChunkSizingDecision(fx.service, null);
        Map<String, Object> log = budgetLog(decision);

        assertEquals(4000, effectiveChunkSizeChars(decision));
        assertFalse((Boolean) log.get("triggeredResharding"));
        assertEquals(4000, ((Number) log.get("baseChunkSizeChars")).intValue());
    }

    @Test
    void resolveChunkSizingDecision_shouldClampBaseChunkSizeToMinimum() throws Exception {
        Fixture fx = new Fixture(cfg(true, null, 120, null, null, null));

        Object decision = invokeResolveChunkSizingDecision(fx.service, cfg(true, null, 120, null, null, null));
        Map<String, Object> log = budgetLog(decision);

        assertEquals(500, effectiveChunkSizeChars(decision));
        assertEquals(500, ((Number) log.get("baseChunkSizeChars")).intValue());
        assertFalse((Boolean) log.get("triggeredResharding"));
    }

    @Test
    void resolveChunkThreshold_shouldCoverFallbackCombinationsAndClampExtremes() {
        ModerationChunkReviewConfigDTO cfgNull = cfg(true, null, null, null, null, null);
        ModerationChunkReviewConfigDTO cfgLow = cfg(true, 800, null, null, null, null);
        ModerationChunkReviewConfigDTO cfgHigh = cfg(true, 2000, null, null, null, null);

        ModerationConfidenceFallbackConfigEntity fbNull = fallbackThreshold(null);
        ModerationConfidenceFallbackConfigEntity fbLow = fallbackThreshold(500);
        ModerationConfidenceFallbackConfigEntity fbHigh = fallbackThreshold(6_000_000);
        ModerationConfidenceFallbackConfigEntity fbNormal = fallbackThreshold(1500);

        assertEquals(20_000, ModerationChunkReviewService.resolveChunkThreshold(cfgNull, fbNull));
        assertEquals(1000, ModerationChunkReviewService.resolveChunkThreshold(cfgLow, null));
        assertEquals(1000, ModerationChunkReviewService.resolveChunkThreshold(cfgHigh, fbLow));
        assertEquals(5_000_000, ModerationChunkReviewService.resolveChunkThreshold(cfgHigh, fbHigh));
        assertEquals(1500, ModerationChunkReviewService.resolveChunkThreshold(cfgLow, fbNormal));
    }

    @Test
    void prepareChunksIfNeeded_shouldCreateOnlyPostChunksWhenOnlyPostExceedsThreshold() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, null, null));
        when(fx.postsRepository.findById(401L)).thenReturn(Optional.of(post(401L, repeat('p', 1800), 1800, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(fx.postAttachmentsRepository.findByPostId(eq(401L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        ModerationChunkSetEntity running = set(91L, ChunkSetStatus.RUNNING, null);
        doReturn(running).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(91L)).thenReturn(List.of(running));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(31L, 401L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertEquals(91L, out.chunkSetId);
        assertTrue(running.getTotalChunks() > 0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModerationChunkEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fx.chunkRepository).saveAll(captor.capture());
        List<ModerationChunkEntity> chunks = captor.getValue();
        assertTrue(chunks.stream().allMatch(c -> c.getSourceType() == ChunkSourceType.POST_TEXT));
    }

    @Test
    void prepareChunksIfNeeded_shouldCreateOnlyFileChunksWhenOnlyFileExceedsThreshold() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, null, null));
        when(fx.postsRepository.findById(402L)).thenReturn(Optional.of(post(402L, repeat('p', 900), 900, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        PostAttachmentsEntity att = attachment(402L, 801L, "file-only.txt");
        when(fx.postAttachmentsRepository.findByPostId(eq(402L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(att)));
        when(fx.fileAssetExtractionsRepository.findAllById(any()))
                .thenReturn(List.of(extraction(801L, FileAssetExtractionStatus.READY, repeat('f', 1600))));

        ModerationChunkSetEntity running = set(92L, ChunkSetStatus.RUNNING, null);
        doReturn(running).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(92L)).thenReturn(List.of(running));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(32L, 402L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertEquals(92L, out.chunkSetId);
        assertTrue(running.getTotalChunks() > 0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModerationChunkEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fx.chunkRepository).saveAll(captor.capture());
        List<ModerationChunkEntity> chunks = captor.getValue();
        assertTrue(chunks.stream().allMatch(c -> c.getSourceType() == ChunkSourceType.FILE_TEXT));
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

    private static ModerationChunkReviewConfigDTO cfg(Boolean enabled,
                                                      Integer threshold,
                                                      Integer chunkSize,
                                                      Integer overlap,
                                                      Integer maxChunks) {
        return cfg(enabled, threshold, chunkSize, overlap, maxChunks, null);
    }

    private static ModerationConfidenceFallbackConfigEntity fallbackThreshold(Integer threshold) {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setChunkThresholdChars(threshold);
        return fb;
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

    private static PostAttachmentsEntity attachment(Long postId, Long fileAssetId, String fileName) {
        PostAttachmentsEntity att = new PostAttachmentsEntity();
        att.setPostId(postId);
        att.setFileAssetId(fileAssetId);
        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setOriginalName(fileName);
        att.setFileAsset(fa);
        return att;
    }

    private static FileAssetExtractionsEntity extraction(Long fileAssetId, FileAssetExtractionStatus status, String text) {
        FileAssetExtractionsEntity e = new FileAssetExtractionsEntity();
        e.setFileAssetId(fileAssetId);
        e.setExtractStatus(status);
        e.setExtractedText(text);
        return e;
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
