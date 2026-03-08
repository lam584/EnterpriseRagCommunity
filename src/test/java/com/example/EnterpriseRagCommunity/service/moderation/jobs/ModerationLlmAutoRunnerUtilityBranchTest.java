package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationLlmAutoRunnerUtilityBranchTest {

    @Test
    void normalizeSuggestion_shouldMapFallbackAliasAndKeyword() throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("normalizeSuggestion", String.class, String.class);
        m.setAccessible(true);

        assertEquals("ESCALATE", m.invoke(null, null, null));
        assertEquals("ALLOW", m.invoke(null, "", "APPROVE"));
        assertEquals("REJECT", m.invoke(null, "please reject this", null));
        assertEquals("ESCALATE", m.invoke(null, "human", null));
    }

    @Test
    void asStringList_shouldSupportListAndSingleValue() throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("asStringList", Object.class);
        m.setAccessible(true);

        Object fromList = m.invoke(null, List.of(" a ", "", "b"));
        assertEquals(List.of("a", "b"), fromList);

        Object fromSingle = m.invoke(null, "  x  ");
        assertEquals(List.of("x"), fromSingle);

        Object fromBlank = m.invoke(null, "   ");
        assertEquals(List.of(), fromBlank);
    }

    @Test
    void deepGet_shouldReturnNestedValueOrNull() throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("deepGet", Map.class, String.class);
        m.setAccessible(true);

        Map<String, Object> root = Map.of("a", Map.of("b", Map.of("c", 1)));
        assertEquals(1, m.invoke(null, root, "a.b.c"));
        assertNull(m.invoke(null, root, "a.missing.c"));
        assertNull(m.invoke(null, Map.of(), "a.b"));
    }

    @Test
    void parseUsedImageIndices_shouldParseDistinctIndices() {
        Set<Integer> out = ModerationLlmAutoRunner.parseUsedImageIndices("x [[IMAGE_1]] y [[IMAGE_2]] z [[IMAGE_1]]");
        assertEquals(Set.of(1, 2), out);
    }

    @Test
    void filterChunkEvidence_shouldDropBlankAndTrim() {
        List<String> out = ModerationLlmAutoRunner.filterChunkEvidence(List.of(" a ", " ", "\t", "b"));
        assertEquals(List.of("a", "b"), out);
        assertEquals(List.of(), ModerationLlmAutoRunner.filterChunkEvidence(null));
    }

    @Test
    void extractByContextAnchors_shouldReturnNullWhenAnchorInvalid() {
        assertNull(ModerationLlmAutoRunner.extractByContextAnchors(Map.of("before_context", " "), "chunk"));
        assertNull(ModerationLlmAutoRunner.extractByContextAnchors(Map.of("before_context", "abc"), " "));
    }

    @Test
    void extractByContextAnchors_shouldExtractMiddleSnippet() {
        String chunk = "前文 违规词 这里是命中的片段 结束后文";
        String out = ModerationLlmAutoRunner.extractByContextAnchors(
                Map.of("before_context", "违规词", "after_context", "结束后文"),
                chunk
        );
        assertNotNull(out);
        assertTrue(out.contains("这里是命中的片段"));
    }

    @Test
    void buildEvidenceNormalizeReplay_shouldRespectDiffAndMax() {
        List<String> out = ModerationLlmAutoRunner.buildEvidenceNormalizeReplay(
                List.of("a", "b", "c"),
                List.of("a", "x", "y"),
                1
        );
        assertEquals(1, out.size());
        assertTrue(out.get(0).contains("idx=1"));
    }

    @Test
    void filterChunkImageEvidence_shouldKeepOnlyAllowedPlaceholders() {
        List<String> evidence = List.of("命中 [[IMAGE_1]] [[IMAGE_2]]", "无图命中");
        List<ModerationLlmAutoRunner.ChunkImageRef> refs = List.of(
                new ModerationLlmAutoRunner.ChunkImageRef(1, "[[IMAGE_1]]", "u1", "image/png", 9L)
        );
        List<String> out = ModerationLlmAutoRunner.filterChunkImageEvidence(evidence, refs);
        assertEquals(List.of("[[IMAGE_1]]"), out);
    }

    @Test
    void parseImageIndexFromPlaceholder_shouldHandleInvalidAndValid() throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("parseImageIndexFromPlaceholder", String.class);
        m.setAccessible(true);

        assertNull(m.invoke(null, "x"));
        assertEquals(18, m.invoke(null, "[[IMAGE_18]]"));
    }

    @Test
    void containsNormalizedText_shouldSupportQuoteNormalization() throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("containsNormalizedText", String.class, String.class);
        m.setAccessible(true);

        boolean direct = (boolean) m.invoke(null, "abc def", "abc");
        boolean normalized = (boolean) m.invoke(null, "“quoted text here”", "\"quoted text here\"");
        boolean fail = (boolean) m.invoke(null, "abc", "xyz");

        assertTrue(direct);
        assertTrue(normalized);
        assertFalse(fail);
    }
}
