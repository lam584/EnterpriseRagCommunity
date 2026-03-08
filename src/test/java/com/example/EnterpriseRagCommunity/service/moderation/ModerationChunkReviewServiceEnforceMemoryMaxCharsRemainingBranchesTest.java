package com.example.EnterpriseRagCommunity.service.moderation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationChunkReviewServiceEnforceMemoryMaxCharsRemainingBranchesTest {

    @Test
    void enforceMemoryMaxChars_shouldCoverInvalidEntryBranchesAcrossThreeMapStages() throws Exception {
        Map<Object, Object> snippetByChunk = new StackAwareInvalidEntryMap();
        Map<Object, Object> summaries = new StackAwareInvalidEntryMap();
        Map<Object, Object> llmEvidenceByChunk = new StackAwareInvalidEntryMap();

        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("chunkTextSnippetByChunk", snippetByChunk);
        mem.put("summaries", summaries);
        mem.put("llmEvidenceByChunk", llmEvidenceByChunk);
        mem.put("stable", "X".repeat(1800));

        invokeEnforceMemoryMaxChars(mem, 500);

        assertSame(snippetByChunk, mem.get("chunkTextSnippetByChunk"));
        assertSame(summaries, mem.get("summaries"));
        assertSame(llmEvidenceByChunk, mem.get("llmEvidenceByChunk"));
        assertTrue("X".repeat(1800).equals(mem.get("stable")));
    }

    private static void invokeEnforceMemoryMaxChars(Map<String, Object> mem, int maxChars) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("enforceMemoryMaxChars", Map.class, int.class);
        m.setAccessible(true);
        m.invoke(null, mem, maxChars);
    }

    private static final class StackAwareInvalidEntryMap extends AbstractMap<Object, Object> {
        private int entrySetCalls = 0;

        @Override
        public Set<Entry<Object, Object>> entrySet() {
            entrySetCalls += 1;
            if (entrySetCalls > 3) {
                LinkedHashSet<Entry<Object, Object>> invalid = new LinkedHashSet<>();
                invalid.add(null);
                invalid.add(new SimpleEntry<>(null, "V"));
                return invalid;
            }
            LinkedHashSet<Entry<Object, Object>> safe = new LinkedHashSet<>();
            safe.add(new SimpleEntry<>("safe", "S".repeat(120)));
            return safe;
        }
    }
}
