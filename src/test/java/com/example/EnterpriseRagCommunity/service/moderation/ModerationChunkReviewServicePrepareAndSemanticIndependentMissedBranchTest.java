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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServicePrepareAndSemanticIndependentMissedBranchTest {

    @Test
    void prepareChunksIfNeeded_shouldThrowWhenQueueIsNullOrQueueIdNull() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 50, 20, null));
        assertThrows(IllegalArgumentException.class, () -> fx.service.prepareChunksIfNeeded(null));
        assertThrows(IllegalArgumentException.class, () -> fx.service.prepareChunksIfNeeded(queue(null, 501L)));
    }

    @Test
    void prepareChunksIfNeeded_shouldReturnNotChunkedWhenPostMissing() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 50, 20, null));
        when(fx.postsRepository.findById(601L)).thenReturn(Optional.empty());

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(61L, 601L));

        assertTrue(out.enabled);
        assertFalse(out.chunked);
        verify(fx.chunkSetRepository, never()).save(any());
    }

    @Test
    void prepareChunksIfNeeded_shouldContinueWhenLockedFirstRowNullAndSkipInvalidExtractions() {
        Fixture fx = new Fixture(cfg(true, 1000, 500, 50, 20, null));
        when(fx.postsRepository.findById(602L)).thenReturn(Optional.of(post(602L, repeat('p', 1600), 1600, false, false)));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(fx.postAttachmentsRepository.findByPostId(eq(602L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(attachment(602L, 901L), attachment(602L, null))));

        when(fx.fileAssetExtractionsRepository.findAllById(any())).thenReturn(Arrays.asList(
                null,
                extraction(null, FileAssetExtractionStatus.READY, repeat('x', 1500)),
                extraction(901L, FileAssetExtractionStatus.READY, null),
                extraction(902L, FileAssetExtractionStatus.FAILED, repeat('x', 1500))
        ));

        ModerationChunkSetEntity running = set(93L, ChunkSetStatus.RUNNING, null);
        doReturn(running).when(fx.service).ensureChunkSetForQueue(any());
        when(fx.chunkSetRepository.findByIdForUpdate(93L)).thenReturn(Arrays.asList((ModerationChunkSetEntity) null));

        ModerationChunkReviewService.ChunkWorkResult out = fx.service.prepareChunksIfNeeded(queue(62L, 602L));

        assertTrue(out.enabled);
        assertTrue(out.chunked);
        assertEquals(93L, out.chunkSetId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModerationChunkEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(fx.chunkRepository).saveAll(captor.capture());
        List<ModerationChunkEntity> chunks = captor.getValue();
        assertTrue(chunks.stream().allMatch(c -> c.getSourceType() == ChunkSourceType.POST_TEXT));
    }

    @Test
    void chunkSemantic_shouldClampSizeAndMaxChunksWhenValuesAreTooSmall() throws Exception {
        List<?> spans = invokeChunkSemantic(repeat('a', 1800), 100, 20, 0);
        assertEquals(1, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(500, spanEnd(spans.get(0)));
    }

    @Test
    void chunkSemantic_shouldUseExactBreakpointWhenTargetStartHitsBoundary() throws Exception {
        List<?> spans = invokeChunkSemantic(semanticTextWithPeriodEvery100(1300), 500, 100, 4);
        assertTrue(spans.size() >= 3);
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(500, spanEnd(spans.get(0)));
        assertEquals(400, spanStart(spans.get(1)));
        assertEquals(900, spanEnd(spans.get(1)));
        assertTrue(spans.stream().anyMatch(s -> {
            try {
                return spanStart(s) == 800;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Test
    void chunkSemantic_shouldRecognizeExtendedSeparators() throws Exception {
        String text = "a\rb?c!d。e？f！g;h";
        List<?> spans = invokeChunkSemantic(text, 500, 0, 20);
        assertEquals(1, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(text.length(), spanEnd(spans.get(0)));
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

    private static PostAttachmentsEntity attachment(Long postId, Long fileAssetId) {
        PostAttachmentsEntity att = new PostAttachmentsEntity();
        att.setPostId(postId);
        att.setFileAssetId(fileAssetId);
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

    private static String semanticTextWithPeriodEvery100(int totalLength) {
        StringBuilder sb = new StringBuilder(totalLength);
        for (int i = 1; i <= totalLength; i++) {
            sb.append(i % 100 == 0 ? '.' : 'a');
        }
        return sb.toString();
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
