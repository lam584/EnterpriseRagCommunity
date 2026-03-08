package com.example.EnterpriseRagCommunity.service.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationChunkReviewServiceEnforceMemoryMaxCharsDeepSupplementTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void enforceMemoryMaxChars_shouldBreakWhenFieldsAreAbsentOrEmpty() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("chunkTextSnippetByChunk", new LinkedHashMap<>());
        mem.put("evidence", new ArrayList<>());
        mem.put("entities", new ArrayList<>());
        mem.put("riskTags", new ArrayList<>());
        mem.put("openQuestions", new ArrayList<>());
        mem.put("stable", "X".repeat(1500));

        invokeEnforceMemoryMaxChars(mem, 500);

        assertTrue(mem.containsKey("chunkTextSnippetByChunk"));
        assertTrue(mem.containsKey("evidence"));
        assertTrue(mem.containsKey("entities"));
        assertTrue(mem.containsKey("riskTags"));
        assertTrue(mem.containsKey("openQuestions"));
        assertFalse(mem.containsKey("summary"));
        assertFalse(mem.containsKey("summaries"));
        assertFalse(mem.containsKey("llmEvidenceByChunk"));
        assertFalse(mem.containsKey("prevSummary"));
        assertTrue(MAPPER.writeValueAsString(mem).length() > 500);
    }

    @Test
    void enforceMemoryMaxChars_shouldProcessAllNonEmptyFieldsThroughMultiRoundLoopThenExit() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("chunkTextSnippetByChunk", new LinkedHashMap<>(Map.of(
                "0", "A".repeat(200),
                "1", "B".repeat(200)
        )));
        mem.put("evidence", new ArrayList<>(List.of("E".repeat(200))));
        mem.put("entities", new ArrayList<>(List.of(Map.of("type", "org", "value", "V".repeat(200)))));
        mem.put("prevSummary", "P".repeat(20));
        mem.put("summary", "S".repeat(200));
        mem.put("summaries", new LinkedHashMap<>(Map.of("0", "SS".repeat(100))));
        mem.put("llmEvidenceByChunk", new LinkedHashMap<>(Map.of("0", List.of("L".repeat(200)))));
        mem.put("riskTags", new ArrayList<>(List.of("R".repeat(200))));
        mem.put("openQuestions", new ArrayList<>(List.of("Q".repeat(200))));
        mem.put("stable", "X".repeat(2200));

        invokeEnforceMemoryMaxChars(mem, 500);

        assertFalse(mem.containsKey("chunkTextSnippetByChunk"));
        assertFalse(mem.containsKey("evidence"));
        assertFalse(mem.containsKey("entities"));
        assertFalse(mem.containsKey("prevSummary"));
        assertFalse(mem.containsKey("summary"));
        assertFalse(mem.containsKey("summaries"));
        assertFalse(mem.containsKey("llmEvidenceByChunk"));
        assertFalse(mem.containsKey("riskTags"));
        assertFalse(mem.containsKey("openQuestions"));
        assertEquals("X".repeat(2200), mem.get("stable"));
        assertTrue(MAPPER.writeValueAsString(mem).length() > 500);
    }

    @Test
    void enforceMemoryMaxChars_shouldRemovePrevSummaryAtLengthBoundary20() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("prevSummary", "P".repeat(20));
        mem.put("stable", "X".repeat(1200));

        invokeEnforceMemoryMaxChars(mem, 500);

        assertFalse(mem.containsKey("prevSummary"));
        assertTrue(MAPPER.writeValueAsString(mem).length() > 500);
    }

    @Test
    void enforceMemoryMaxChars_shouldTrimPrevSummaryWhenLengthIs21AndStopWithoutRemoval() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("prevSummary", "P".repeat(21));
        mem.put("stable", "X".repeat(455));

        invokeEnforceMemoryMaxChars(mem, 500);

        assertTrue(mem.containsKey("prevSummary"));
        String current = String.valueOf(mem.get("prevSummary"));
        assertEquals(10, current.length());
        assertTrue(MAPPER.writeValueAsString(mem).length() <= 500);
    }

    private static void invokeEnforceMemoryMaxChars(Map<String, Object> mem, int maxChars) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("enforceMemoryMaxChars", Map.class, int.class);
        m.setAccessible(true);
        m.invoke(null, mem, maxChars);
    }
}
