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

import java.lang.reflect.Method;
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServicePrepareAndSemanticExtremeShortCircuitIndependentTest {

    @Test
    void prepareChunksIfNeeded_shouldUseFileNeedWhenPostLengthNullAndSetIdNull() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, null, 20, "SEMANTIC"));
        when(fx.postsRepository.findById(2201L)).thenReturn(Optional.of(post(2201L, null, null, true, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(fx.postAttachmentsRepository.findByPostId(eq(2201L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Arrays.asList(
                        null,
                        attachment(2201L, null, null),
                        attachment(2201L, 9101L, null),
                        attachment(2201L, 9102L, "doc-2.txt"),
                        attachment(2201L, 9103L, "doc-3.txt")
                )));
        when(fx.fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(
                extraction(9101L, FileAssetExtractionStatus.READY, "   "),
                extraction(9103L, FileAssetExtractionStatus.READY, repeat('m', 1000)),
                extraction(9102L, FileAssetExtractionStatus.READY, repeat('x', 1600))
        ));

        ModerationChunkSetEntity set = set(null, ChunkSetStatus.RUNNING, 0);
        doReturn(set).when(fx.service).ensureChunkSetForQueue(any());

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(301L, 2201L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModerationChunkEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fx.chunkRepository).saveAll(captor.capture());
        List<ModerationChunkEntity> chunks = captor.getValue();
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(c -> c.getSourceType() == ChunkSourceType.FILE_TEXT));
        assertTrue(chunks.stream().allMatch(c -> c.getFileAssetId() != null && c.getFileAssetId().equals(9102L)));
        assertTrue(chunks.stream().allMatch(c -> "doc-2.txt".equals(c.getFileName())));
    }

    @Test
    void prepareChunksIfNeeded_shouldContinueWhenLockedSetHasZeroChunks() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 10, 20, null));
        when(fx.postsRepository.findById(2202L)).thenReturn(Optional.of(post(2202L, repeat('p', 1600), 1600, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(fx.postAttachmentsRepository.findByPostId(eq(2202L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        ModerationChunkSetEntity initial = set(920L, ChunkSetStatus.RUNNING, 0);
        ModerationChunkSetEntity locked = set(920L, ChunkSetStatus.RUNNING, 0);
        doReturn(initial).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(920L)).thenReturn(List.of(locked));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(302L, 2202L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModerationChunkEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fx.chunkRepository).saveAll(captor.capture());
        assertTrue(captor.getValue().stream().allMatch(c -> c.getSourceType() == ChunkSourceType.POST_TEXT));
    }

    @Test
    void prepareChunksIfNeeded_shouldEvaluateNullReadyExtractionTextWithoutFileChunkNeed() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 10, 20, null));
        when(fx.postsRepository.findById(2203L)).thenReturn(Optional.of(post(2203L, repeat('p', 1800), 1800, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(fx.postAttachmentsRepository.findByPostId(eq(2203L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(attachment(2203L, 9301L, "doc-null.txt"))));
        when(fx.fileAssetExtractionsRepository.findAllById(any()))
                .thenReturn(List.of(extraction(9301L, FileAssetExtractionStatus.READY, null)));

        ModerationChunkSetEntity set = set(930L, ChunkSetStatus.RUNNING, null);
        doReturn(set).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(930L)).thenReturn(List.of(set));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(303L, 2203L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModerationChunkEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fx.chunkRepository).saveAll(captor.capture());
        List<ModerationChunkEntity> chunks = captor.getValue();
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(c -> c.getSourceType() == ChunkSourceType.POST_TEXT));
    }

    @Test
    void chunkSemantic_shouldStopByMaxChunksEvenWhenTextStillRemaining() throws Exception {
        String text = repeat('s', 1600);
        List<?> spans = invokeChunkSemantic(text, 500, 0, 1);
        assertEquals(1, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(500, spanEnd(spans.get(0)));
    }

    @Test
    void chunkSemantic_shouldAdvanceWhenNegativeTargetStartFallsBackToCursorPlusOne() throws Exception {
        String text = "\n" + repeat('a', 1300);
        List<?> spans = invokeChunkSemantic(text, 500, 200, 4);
        assertTrue(spans.size() >= 3);
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(1, spanEnd(spans.get(0)));
        assertEquals(1, spanStart(spans.get(1)));
        assertTrue(spans.stream().allMatch(s -> {
            try {
                return spanEnd(s) > spanStart(s);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
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

    @SuppressWarnings("unchecked")
    private static List<?> invokeChunkSemantic(String text, Integer chunkSizeChars, Integer overlapChars, Integer maxChunksTotal) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("chunkSemantic", String.class, Integer.class, Integer.class, Integer.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(null, text, chunkSizeChars, overlapChars, maxChunksTotal);
    }

    private static int spanStart(Object span) throws Exception {
        Method m = span.getClass().getDeclaredMethod("start");
        m.setAccessible(true);
        return (Integer) m.invoke(span);
    }

    private static int spanEnd(Object span) throws Exception {
        Method m = span.getClass().getDeclaredMethod("end");
        m.setAccessible(true);
        return (Integer) m.invoke(span);
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
