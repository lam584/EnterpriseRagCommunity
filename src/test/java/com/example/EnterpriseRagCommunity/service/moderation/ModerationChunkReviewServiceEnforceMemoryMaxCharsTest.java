package com.example.EnterpriseRagCommunity.service.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationChunkReviewServiceEnforceMemoryMaxCharsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void enforceMemoryMaxChars_shouldThrowWhenJsonSerializationFails() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("self", mem);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> invokeEnforceMemoryMaxChars(mem, 800));

        assertTrue(ex.getCause() != null);
    }

    @Test
    void enforceMemoryMaxChars_shouldTrimPrevSummaryStepByStep() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("prevSummary", "P".repeat(2200));
        mem.put("stable", "ok");

        invokeEnforceMemoryMaxChars(mem, 500);
        String json = MAPPER.writeValueAsString(mem);

        assertTrue(json.length() <= 500);
        assertTrue(mem.containsKey("prevSummary"));
        assertTrue(String.valueOf(mem.get("prevSummary")).length() >= 20);
        assertTrue(String.valueOf(mem.get("prevSummary")).length() < 2200);
        assertTrue("ok".equals(mem.get("stable")));
    }

    @Test
    void enforceMemoryMaxChars_shouldRemoveSummaryAndLlmEvidenceByChunkWhenOversized() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("summary", "S".repeat(260));
        mem.put("llmEvidenceByChunk", new LinkedHashMap<>(Map.of(
                "0", new ArrayList<>(List.of("L".repeat(260)))
        )));
        mem.put("stable", "X".repeat(340));

        invokeEnforceMemoryMaxChars(mem, 500);

        assertFalse(mem.containsKey("summary"));
        assertFalse(mem.containsKey("llmEvidenceByChunk"));
        assertTrue(MAPPER.writeValueAsString(mem).length() <= 500);
    }

    @Test
    void enforceMemoryMaxChars_shouldRemoveEvidenceEntitiesPrevSummaryRiskTagsAndOpenQuestionsWhenOversized() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("evidence", new ArrayList<>(List.of("E".repeat(220), "F".repeat(220))));
        mem.put("entities", new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("type", "person", "value", "G".repeat(220))),
                new LinkedHashMap<>(Map.of("type", "org", "value", "H".repeat(220)))
        )));
        mem.put("prevSummary", "P".repeat(18));
        mem.put("riskTags", new ArrayList<>(List.of("r".repeat(220))));
        mem.put("openQuestions", new ArrayList<>(List.of("Q".repeat(220))));
        mem.put("stable", "X".repeat(340));

        invokeEnforceMemoryMaxChars(mem, 500);

        assertFalse(mem.containsKey("evidence"));
        assertFalse(mem.containsKey("entities"));
        assertFalse(mem.containsKey("prevSummary"));
        assertFalse(mem.containsKey("riskTags"));
        assertFalse(mem.containsKey("openQuestions"));
        assertTrue(MAPPER.writeValueAsString(mem).length() <= 500);
    }

    private static void invokeEnforceMemoryMaxChars(Map<String, Object> mem, int maxChars) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("enforceMemoryMaxChars", Map.class, int.class);
        m.setAccessible(true);
        m.invoke(null, mem, maxChars);
    }
}
