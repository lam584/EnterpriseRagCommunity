package com.example.EnterpriseRagCommunity.service.moderation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationChunkReviewServiceChunkSemanticRemainingBranchesTest {

    @Test
    void chunkSemantic_shouldReturnEmptyWhenTextIsBlank() throws Exception {
        List<?> spans = invokeChunkSemantic("", 500, 50, 3);
        assertTrue(spans.isEmpty());
    }

    @Test
    void chunkSemantic_shouldHandleLeadingBlankParagraphsWithoutStalling() throws Exception {
        String text = "\n\n" + "a".repeat(1198);
        List<?> spans = invokeChunkSemantic(text, 500, 50, 20);

        assertFalse(spans.isEmpty());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(2, spanEnd(spans.get(0)));
        assertEquals(text.length(), spanEnd(spans.get(spans.size() - 1)));
        for (Object span : spans) {
            assertTrue(spanEnd(span) > spanStart(span));
        }
    }

    @Test
    void chunkSemantic_shouldHardCutForLongTokenAndKeepConfiguredOverlap() throws Exception {
        String text = "x".repeat(1600);
        List<?> spans = invokeChunkSemantic(text, 500, 50, 4);

        assertEquals(4, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(500, spanEnd(spans.get(0)));
        assertEquals(450, spanStart(spans.get(1)));
        assertEquals(950, spanEnd(spans.get(1)));
        assertEquals(900, spanStart(spans.get(2)));
        assertEquals(1400, spanEnd(spans.get(2)));
        assertEquals(1350, spanStart(spans.get(3)));
        assertEquals(1600, spanEnd(spans.get(3)));
    }

    @Test
    void chunkSemantic_shouldClampOverlapAtBoundaryWhenOverlapExceedsChunkSize() throws Exception {
        String text = semanticTextWithPeriodEvery100(1200);
        List<?> spans = invokeChunkSemantic(text, 500, 800, 3);

        assertEquals(3, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(500, spanEnd(spans.get(0)));
        assertEquals(400, spanStart(spans.get(1)));
        assertEquals(900, spanEnd(spans.get(1)));
    }

    @Test
    void chunkSemantic_shouldStopByMaxChunksBoundaryWhenTextStillHasRemaining() throws Exception {
        String text = "x".repeat(2200);
        List<?> spans = invokeChunkSemantic(text, 500, 0, 2);

        assertEquals(2, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(500, spanEnd(spans.get(0)));
        assertEquals(500, spanStart(spans.get(1)));
        assertEquals(1000, spanEnd(spans.get(1)));
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
}
