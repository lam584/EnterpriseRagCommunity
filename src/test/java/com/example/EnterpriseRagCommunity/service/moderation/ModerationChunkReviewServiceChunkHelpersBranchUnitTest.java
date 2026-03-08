package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceChunkHelpersBranchUnitTest {

    @Test
    void chunkSpans_shouldClampSizeOverlapAndRespectMaxChunks() throws Exception {
        List<?> spans = invokeChunkSpans(2000, 300, 800, 2);
        assertEquals(2, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(500, spanEnd(spans.get(0)));
        assertEquals(450, spanStart(spans.get(1)));
        assertEquals(950, spanEnd(spans.get(1)));
    }

    @Test
    void chunkSpans_shouldReturnSingleSpanWhenContentShorterThanChunk() throws Exception {
        List<?> spans = invokeChunkSpans(120, null, null, null);
        assertEquals(1, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(120, spanEnd(spans.get(0)));
    }

    @Test
    void chunkSemantic_shouldReturnEmptyWhenTextNull() throws Exception {
        List<?> spans = invokeChunkSemantic(null, 1000, 50, 10);
        assertTrue(spans.isEmpty());
    }

    @Test
    void chunkSemantic_shouldUseHardCutAndOverlapFallbackWhenNoBreakpoints() throws Exception {
        String text = "a".repeat(1200);
        List<?> spans = invokeChunkSemantic(text, 500, 50, 3);
        assertEquals(3, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(500, spanEnd(spans.get(0)));
        assertEquals(450, spanStart(spans.get(1)));
        assertEquals(950, spanEnd(spans.get(1)));
        assertEquals(900, spanStart(spans.get(2)));
        assertEquals(1200, spanEnd(spans.get(2)));
    }

    @Test
    void chunkSemantic_shouldPreferBreakpointsAndMoveCursorToNearestBoundary() throws Exception {
        String text = semanticTextWithPeriodEvery100(1200);
        List<?> spans = invokeChunkSemantic(text, 500, 50, 3);
        assertEquals(3, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(500, spanEnd(spans.get(0)));
        assertEquals(400, spanStart(spans.get(1)));
        assertEquals(900, spanEnd(spans.get(1)));
    }

    @Test
    void sliceSafe_shouldHandleNullAndOutOfRange() throws Exception {
        assertEquals("", invokeSliceSafe(null, 1, 2));
        assertEquals("", invokeSliceSafe("abc", 9, 12));
        assertEquals("abc", invokeSliceSafe("abcd", -2, 3));
    }

    @Test
    void safeLoadExtractions_shouldReturnEmptyWhenAttachmentsInvalid() throws Exception {
        Fixture fx = new Fixture();
        List<PostAttachmentsEntity> atts = new ArrayList<>();
        atts.add(null);
        atts.add(attachmentWithFileId(null));
        List<FileAssetExtractionsEntity> out = invokeSafeLoadExtractions(fx.service, atts);
        assertTrue(out.isEmpty());
        verify(fx.fileAssetExtractionsRepository, never()).findAllById(any());
    }

    @Test
    void safeLoadExtractions_shouldLoadDistinctIdsAndLimitTo200() throws Exception {
        Fixture fx = new Fixture();
        List<PostAttachmentsEntity> atts = new ArrayList<>();
        for (long i = 1; i <= 210; i++) atts.add(attachmentWithFileId(i));
        atts.add(attachmentWithFileId(2L));
        atts.add(attachmentWithFileId(3L));

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(1L);
        when(fx.fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(ex));

        List<FileAssetExtractionsEntity> out = invokeSafeLoadExtractions(fx.service, atts);
        assertEquals(1, out.size());
        assertEquals(1L, out.get(0).getFileAssetId());

        ArgumentCaptor<Iterable<Long>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(fx.fileAssetExtractionsRepository).findAllById(captor.capture());
        int idCount = 0;
        for (Long ignored : captor.getValue()) idCount += 1;
        assertEquals(200, idCount);
    }

    @Test
    void safeLoadExtractions_shouldReturnEmptyWhenRepositoryThrows() throws Exception {
        Fixture fx = new Fixture();
        when(fx.fileAssetExtractionsRepository.findAllById(any())).thenThrow(new RuntimeException("db"));
        List<FileAssetExtractionsEntity> out = invokeSafeLoadExtractions(fx.service, List.of(attachmentWithFileId(8L)));
        assertTrue(out.isEmpty());
    }

    private static PostAttachmentsEntity attachmentWithFileId(Long fileAssetId) {
        PostAttachmentsEntity att = new PostAttachmentsEntity();
        att.setFileAssetId(fileAssetId);
        return att;
    }

    private static String semanticTextWithPeriodEvery100(int totalLength) {
        StringBuilder sb = new StringBuilder(totalLength);
        for (int i = 1; i <= totalLength; i++) {
            sb.append(i % 100 == 0 ? '.' : 'a');
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<?> invokeChunkSpans(int len, Integer chunkSizeChars, Integer overlapChars, Integer maxChunksTotal) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("chunkSpans", int.class, Integer.class, Integer.class, Integer.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(null, len, chunkSizeChars, overlapChars, maxChunksTotal);
    }

    @SuppressWarnings("unchecked")
    private static List<?> invokeChunkSemantic(String text, Integer chunkSizeChars, Integer overlapChars, Integer maxChunksTotal) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("chunkSemantic", String.class, Integer.class, Integer.class, Integer.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(null, text, chunkSizeChars, overlapChars, maxChunksTotal);
    }

    private static String invokeSliceSafe(String text, int start, int end) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("sliceSafe", String.class, int.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, text, start, end);
    }

    @SuppressWarnings("unchecked")
    private static List<FileAssetExtractionsEntity> invokeSafeLoadExtractions(ModerationChunkReviewService service, List<PostAttachmentsEntity> atts) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("safeLoadExtractions", List.class);
        m.setAccessible(true);
        return (List<FileAssetExtractionsEntity>) m.invoke(service, atts);
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
        final ModerationChunkReviewService service = new ModerationChunkReviewService(
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
        );
    }
}
