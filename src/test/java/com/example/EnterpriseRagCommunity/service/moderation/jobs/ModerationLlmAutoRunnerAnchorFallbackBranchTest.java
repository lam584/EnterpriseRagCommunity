package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationLlmAutoRunnerAnchorFallbackBranchTest {

    @Test
    void anchorUtilities_shouldCoverFallbackAndBoundaryBranches() throws Exception {
        Method extractBetween = method("extractBetweenAnchorsByRegex", String.class, String.class, String.class, int.class);
        Method fallbackSnippet = method("fallbackViolationSnippet", String.class, int.class);
        Method findBoundaryEnd = method("findBoundaryEnd", String.class, int.class, int.class);
        Method cleanSnippet = method("cleanExtractedSnippet", String.class);
        Method anchorToRegex = method("anchorToRegex", String.class);
        Method normalizeForAnchorRegex = method("normalizeForAnchorRegex", String.class);
        Method clipText = method("clipTextForEvidenceJson", String.class, int.class);
        Method canonicalEvidenceValue = method("canonicalEvidenceValue", Object.class);

        String noAfterText = "前文 风险描述 命中短语，这里继续说明。[[IMAGE_1]]\n[SECTION]";
        String extracted = (String) extractBetween.invoke(null, noAfterText, "风险描述", null, 120);
        assertNotNull(extracted);
        assertTrue(extracted.contains("命中短语"));

        String noMatch = "完全不匹配的文本";
        Object none = extractBetween.invoke(null, noMatch, "不存在锚点", "也不存在", 80);
        assertEquals(null, none);

        String fallbackText = "A before_context 违规内容,继续补充\n[BLOCK]";
        String fallback = (String) fallbackSnippet.invoke(null, fallbackText, fallbackText.indexOf("违规内容"));
        assertNotNull(fallback);
        assertTrue(fallback.contains("违规内容"));

        int boundary = (int) findBoundaryEnd.invoke(null, "abc,def", 0, 7);
        assertEquals(3, boundary);

        String cleaned = (String) cleanSnippet.invoke(null, "  [[IMAGE_2]]\n违规\t片段  ");
        assertEquals("违规 片段", cleaned);

        assertEquals("\\Qbefore\\E\\s+\\Qcontext\\E", anchorToRegex.invoke(null, " before   context "));
        assertEquals("\"abc\"and'def'", normalizeForAnchorRegex.invoke(null, " “abc” and ‘def’ "));

        String clipped = (String) clipText.invoke(null, "x".repeat(100), 10);
        assertEquals(20, clipped.length());
        assertEquals("违规 内容", canonicalEvidenceValue.invoke(null, "  [[IMAGE_9]] 违规  内容 "));
    }

    private static Method method(String name, Class<?>... types) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }
}
