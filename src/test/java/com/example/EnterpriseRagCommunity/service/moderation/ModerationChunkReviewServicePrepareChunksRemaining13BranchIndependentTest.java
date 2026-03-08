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

import java.util.Arrays;
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

class ModerationChunkReviewServicePrepareChunksRemaining13BranchIndependentTest {

    @Test
    void prepareChunksIfNeeded_shouldReturnNotChunkedWhenChunkSetIsNull() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, 20));
        when(fx.postsRepository.findById(1001L)).thenReturn(Optional.of(post(1001L, repeat('p', 1500), 1500, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        doReturn(null).when(fx.service).ensureChunkSetForQueue(any());

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(201L, 1001L));

        assertTrue(out.enabled);
        assertFalse(out.chunked);
        verify(fx.chunkRepository, never()).saveAll(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldContinueWhenLockedFirstElementIsNull() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, 20));
        when(fx.postsRepository.findById(1002L)).thenReturn(Optional.of(post(1002L, repeat('p', 1600), 1600, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        ModerationChunkSetEntity running = set(902L, ChunkSetStatus.RUNNING, null);
        doReturn(running).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(902L)).thenReturn(Arrays.asList((ModerationChunkSetEntity) null));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(202L, 1002L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertEquals(902L, out.chunkSetId);
        verify(fx.chunkRepository).saveAll(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldOnlyCreateFileChunksWhenPostContentBlank() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, 20));
        when(fx.postsRepository.findById(1003L)).thenReturn(Optional.of(post(1003L, "   ", 0, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(fx.postAttachmentsRepository.findByPostId(eq(1003L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(attachment(1003L, 9031L, "f1.txt"))));
        when(fx.fileAssetExtractionsRepository.findAllById(any()))
                .thenReturn(List.of(extraction(9031L, FileAssetExtractionStatus.READY, repeat('f', 1800))));

        ModerationChunkSetEntity running = set(903L, ChunkSetStatus.RUNNING, null);
        doReturn(running).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(903L)).thenReturn(List.of(running));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(203L, 1003L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModerationChunkEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fx.chunkRepository).saveAll(captor.capture());
        List<ModerationChunkEntity> chunks = captor.getValue();
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(c -> c.getSourceType() == ChunkSourceType.FILE_TEXT));
    }

    @Test
    void prepareChunksIfNeeded_shouldKeepFileNameNullWhenAttachmentNameMissing() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, 20));
        when(fx.postsRepository.findById(1004L)).thenReturn(Optional.of(post(1004L, repeat('p', 1500), 1500, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(fx.postAttachmentsRepository.findByPostId(eq(1004L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(attachment(1004L, 9041L, null))));
        when(fx.fileAssetExtractionsRepository.findAllById(any()))
                .thenReturn(List.of(extraction(9041L, FileAssetExtractionStatus.READY, repeat('x', 1600))));

        ModerationChunkSetEntity running = set(904L, ChunkSetStatus.RUNNING, null);
        doReturn(running).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(904L)).thenReturn(List.of(running));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(204L, 1004L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModerationChunkEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fx.chunkRepository).saveAll(captor.capture());
        List<ModerationChunkEntity> chunks = captor.getValue();
        List<ModerationChunkEntity> fileChunks = chunks.stream().filter(c -> c.getSourceType() == ChunkSourceType.FILE_TEXT).toList();
        assertFalse(fileChunks.isEmpty());
        assertTrue(fileChunks.stream().allMatch(c -> c.getFileAssetId() != null && c.getFileAssetId().equals(9041L)));
        assertTrue(fileChunks.stream().allMatch(c -> c.getFileName() == null));
    }

    @Test
    void prepareChunksIfNeeded_shouldRespectMaxChunksTotalForPostAndFileSeparately() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 0, 2));
        when(fx.postsRepository.findById(1005L)).thenReturn(Optional.of(post(1005L, repeat('p', 2600), 2600, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(fx.postAttachmentsRepository.findByPostId(eq(1005L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(attachment(1005L, 9051L, "cap.txt"))));
        when(fx.fileAssetExtractionsRepository.findAllById(any()))
                .thenReturn(List.of(extraction(9051L, FileAssetExtractionStatus.READY, repeat('z', 2600))));

        ModerationChunkSetEntity running = set(905L, ChunkSetStatus.RUNNING, null);
        doReturn(running).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(905L)).thenReturn(List.of(running));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(205L, 1005L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModerationChunkEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fx.chunkRepository).saveAll(captor.capture());
        List<ModerationChunkEntity> chunks = captor.getValue();
        long postCount = chunks.stream().filter(c -> c.getSourceType() == ChunkSourceType.POST_TEXT).count();
        long fileCount = chunks.stream().filter(c -> c.getSourceType() == ChunkSourceType.FILE_TEXT).count();
        assertEquals(2L, postCount);
        assertEquals(2L, fileCount);
        assertEquals(4, running.getTotalChunks());
    }

    private static ModerationChunkReviewConfigDTO cfg(Boolean enabled,
                                                      Integer threshold,
                                                      Integer chunkSize,
                                                      Integer overlap,
                                                      Integer maxChunks) {
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

    private static PostAttachmentsEntity attachment(Long postId, Long fileAssetId, String fileName) {
        PostAttachmentsEntity att = new PostAttachmentsEntity();
        att.setPostId(postId);
        att.setFileAssetId(fileAssetId);
        if (fileName != null) {
            FileAssetsEntity fa = new FileAssetsEntity();
            fa.setOriginalName(fileName);
            att.setFileAsset(fa);
        }
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
