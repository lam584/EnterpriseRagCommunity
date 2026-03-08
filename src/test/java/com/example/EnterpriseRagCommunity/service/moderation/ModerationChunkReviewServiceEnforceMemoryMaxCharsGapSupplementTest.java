package com.example.EnterpriseRagCommunity.service.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationChunkReviewServiceEnforceMemoryMaxCharsGapSupplementTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void enforceMemoryMaxChars_shouldReturnImmediatelyWhenMemoryIsNull() throws Exception {
        invokeEnforceMemoryMaxChars(null, 500);
    }

    @Test
    void enforceMemoryMaxChars_shouldStopAtGuardLimitWhenStillOversized() throws Exception {
        LinkedHashMap<String, Object> snippetByChunk = new LinkedHashMap<>();
        for (int i = 0; i < 500; i++) {
            snippetByChunk.put(String.valueOf(i), "X".repeat(40));
        }
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("chunkTextSnippetByChunk", snippetByChunk);
        mem.put("stable", "Y".repeat(3000));

        invokeEnforceMemoryMaxChars(mem, 500);

        Map<?, ?> remained = (Map<?, ?>) mem.get("chunkTextSnippetByChunk");
        assertTrue(remained != null && remained.size() >= 250);
        assertTrue(MAPPER.writeValueAsString(mem).length() > 500);
    }

    @Test
    void enforceMemoryMaxChars_shouldSkipInvalidEntriesInSummariesAndByChunkMaps() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        LinkedHashMap<String, Object> summaries = new LinkedHashMap<>();
        summaries.put(" ", null);
        summaries.put("s0", "S".repeat(320));
        LinkedHashMap<String, Object> byChunk = new LinkedHashMap<>();
        byChunk.put(" ", null);
        byChunk.put("b0", List.of("B".repeat(320)));

        mem.put("summary", "Q".repeat(320));
        mem.put("summaries", summaries);
        mem.put("llmEvidenceByChunk", byChunk);
        mem.put("riskTags", "not-list");
        mem.put("openQuestions", "not-list");
        mem.put("stable", "Z".repeat(420));

        invokeEnforceMemoryMaxChars(mem, 500);

        assertTrue(mem.containsKey("riskTags"));
        assertTrue(mem.containsKey("openQuestions"));
        assertTrue(!mem.containsKey("summary"));
        assertTrue(!mem.containsKey("summaries"));
        assertTrue(!mem.containsKey("llmEvidenceByChunk"));
        assertEquals("not-list", mem.get("riskTags"));
        assertEquals("not-list", mem.get("openQuestions"));
    }

    @Test
    void enforceMemoryMaxChars_shouldTreatEmptyMapsAsNonShrinkableAndContinueToListBranches() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("summaries", new LinkedHashMap<>());
        mem.put("llmEvidenceByChunk", new LinkedHashMap<>());
        mem.put("riskTags", new LinkedHashMap<>(Map.of("k", "v")));
        mem.put("openQuestions", new LinkedHashMap<>(Map.of("k", "v")));
        mem.put("stable", "M".repeat(1800));

        invokeEnforceMemoryMaxChars(mem, 500);

        assertTrue(mem.containsKey("summaries"));
        assertTrue(mem.containsKey("llmEvidenceByChunk"));
        assertTrue(mem.containsKey("riskTags"));
        assertTrue(mem.containsKey("openQuestions"));
        assertTrue(MAPPER.writeValueAsString(mem).length() > 500);
    }

    @Test
    void enforceMemoryMaxChars_shouldRemoveSingleRiskTagAndQuestionListsWhenOversized() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("riskTags", List.of("R".repeat(380)));
        mem.put("openQuestions", List.of("Q".repeat(380)));
        mem.put("stable", "N".repeat(420));

        invokeEnforceMemoryMaxChars(mem, 500);

        assertFalse(mem.containsKey("riskTags"));
        assertFalse(mem.containsKey("openQuestions"));
        assertTrue(MAPPER.writeValueAsString(mem).length() <= 500);
    }

    @Test
    void enforceMemoryMaxChars_shouldTrimRiskAndQuestionListsWithoutRemovingKeys() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("riskTags", List.of("R".repeat(120), "R".repeat(120)));
        mem.put("openQuestions", List.of("Q".repeat(120), "Q".repeat(120)));
        mem.put("stable", "P".repeat(230));

        invokeEnforceMemoryMaxChars(mem, 500);

        if (mem.containsKey("riskTags")) {
            List<?> risk = (List<?>) mem.get("riskTags");
            assertTrue(risk.size() <= 1);
        }
        if (mem.containsKey("openQuestions")) {
            List<?> questions = (List<?>) mem.get("openQuestions");
            assertTrue(questions.size() <= 1);
        }
        assertTrue(MAPPER.writeValueAsString(mem).length() <= 500);
    }

    private static void invokeEnforceMemoryMaxChars(Map<String, Object> mem, int maxChars) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("enforceMemoryMaxChars", Map.class, int.class);
        m.setAccessible(true);
        m.invoke(null, mem, maxChars);
    }
}
