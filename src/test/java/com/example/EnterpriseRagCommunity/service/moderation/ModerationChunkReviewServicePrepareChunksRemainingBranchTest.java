package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServicePrepareChunksRemainingBranchTest {

    @Test
    void prepareChunksIfNeeded_shouldReturnNotChunkedWhenContentTypeIsNotPost() {
        Fixture fx = new Fixture(cfg(true, 1000, null, null, null, null));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(21L, ContentType.COMMENT, 301L));

        assertTrue(out.enabled);
        assertFalse(out.chunked);
        verify(fx.postsRepository, never()).findById(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldReturnNotChunkedWhenPostDeleted() {
        Fixture fx = new Fixture(cfg(true, 1000, null, null, null, null));
        when(fx.postsRepository.findById(302L)).thenReturn(Optional.of(post(302L, repeat('a', 1800), 1800, false, true)));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(22L, ContentType.POST, 302L));

        assertTrue(out.enabled);
        assertFalse(out.chunked);
        verify(fx.chunkSetRepository, never()).save(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldIgnoreNonReadyAndBlankExtractionText() {
        Fixture fx = new Fixture(cfg(true, 1000, null, null, null, null));
        when(fx.postsRepository.findById(303L)).thenReturn(Optional.of(post(303L, repeat('a', 900), 900, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        PostAttachmentsEntity att1 = attachment(303L, 701L, "not-ready.txt");
        PostAttachmentsEntity att2 = attachment(303L, 702L, "blank.txt");
        when(fx.postAttachmentsRepository.findByPostId(eq(303L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(att1, att2)));

        FileAssetExtractionsEntity notReady = extraction(701L, FileAssetExtractionStatus.FAILED, repeat('b', 4000));
        FileAssetExtractionsEntity blankReady = extraction(702L, FileAssetExtractionStatus.READY, "   ");
        when(fx.fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(notReady, blankReady));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(23L, ContentType.POST, 303L));

        assertTrue(out.enabled);
        assertFalse(out.chunked);
        verify(fx.chunkRepository, never()).saveAll(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldReturnCancelledWhenSetTurnsCancelledAfterLock() {
        Fixture fx = new Fixture(cfg(true, 1000, null, null, null, null));
        when(fx.postsRepository.findById(304L)).thenReturn(Optional.of(post(304L, repeat('a', 1600), 1600, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        ModerationChunkSetEntity existing = set(84L, ChunkSetStatus.RUNNING, null);
        ModerationChunkSetEntity lockedCancelled = set(84L, ChunkSetStatus.CANCELLED, null);
        doReturn(existing).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(84L)).thenReturn(List.of(lockedCancelled));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(24L, ContentType.POST, 304L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertTrue(out.cancelled);
        assertEquals(84L, out.chunkSetId);
        verify(fx.chunkRepository, never()).saveAll(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldReturnReadyWhenSetTurnsReadyAfterLock() {
        Fixture fx = new Fixture(cfg(true, 1000, null, null, null, null));
        when(fx.postsRepository.findById(305L)).thenReturn(Optional.of(post(305L, repeat('a', 1600), 1600, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        ModerationChunkSetEntity existing = set(85L, ChunkSetStatus.RUNNING, null);
        ModerationChunkSetEntity lockedReady = set(85L, ChunkSetStatus.RUNNING, 3);
        doReturn(existing).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(85L)).thenReturn(List.of(lockedReady));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(25L, ContentType.POST, 305L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertFalse(out.cancelled);
        assertEquals(85L, out.chunkSetId);
        verify(fx.chunkRepository, never()).saveAll(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldSkipSaveAllWhenNeedTrueButNoChunkCreated() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 10, null, null));
        when(fx.postsRepository.findById(306L)).thenReturn(Optional.of(post(306L, "", 0, true, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        ModerationChunkSetEntity running = set(86L, ChunkSetStatus.RUNNING, null);
        doReturn(running).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(86L)).thenReturn(List.of(running));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(26L, ContentType.POST, 306L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertEquals(0, running.getTotalChunks());
        verify(fx.chunkRepository, never()).saveAll(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldUseSemanticChunkingAndPersistChunks() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, 20, "SEMANTIC"));
        String postText = repeat('a', 480) + "." + repeat('b', 720);
        when(fx.postsRepository.findById(307L)).thenReturn(Optional.of(post(307L, postText, postText.length(), false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(fx.postAttachmentsRepository.findByPostId(eq(307L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        ModerationChunkSetEntity running = set(87L, ChunkSetStatus.RUNNING, null);
        doReturn(running).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(87L)).thenReturn(List.of(running));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(27L, ContentType.POST, 307L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertEquals(87L, out.chunkSetId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModerationChunkEntity>> chunksCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(fx.chunkRepository).saveAll(chunksCaptor.capture());
        List<ModerationChunkEntity> chunks = chunksCaptor.getValue();
        assertFalse(chunks.isEmpty());
        ModerationChunkEntity first = chunks.get(0);
        assertEquals(ChunkSourceType.POST_TEXT, first.getSourceType());
        assertEquals(0, first.getStartOffset());
        assertEquals(481, first.getEndOffset());
        assertTrue(running.getTotalChunks() > 0);
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

    private static ModerationQueueEntity queue(Long id, ContentType type, Long contentId) {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(id);
        q.setContentType(type);
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

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(Math.max(0, n));
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
