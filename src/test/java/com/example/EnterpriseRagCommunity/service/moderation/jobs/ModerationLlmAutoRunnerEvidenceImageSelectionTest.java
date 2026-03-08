package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationLlmAutoRunnerEvidenceImageSelectionTest {

    private static Object select(Map<String, Object> mem,
                                 Integer chunkIndex,
                                 List<ModerationLlmAutoRunner.ChunkImageRef> candidateRefs) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "selectEvidenceDrivenChunkImages",
                Map.class,
                Integer.class,
                List.class
        );
        m.setAccessible(true);
        return m.invoke(null, mem, chunkIndex, candidateRefs);
    }

    @SuppressWarnings("unchecked")
    private static List<ModerationLlmAutoRunner.ChunkImageRef> selectedRefs(Object selection) throws Exception {
        Field f = selection.getClass().getDeclaredField("selectedRefs");
        f.setAccessible(true);
        return (List<ModerationLlmAutoRunner.ChunkImageRef>) f.get(selection);
    }

    @SuppressWarnings("unchecked")
    private static List<String> placeholders(Object selection) throws Exception {
        Field f = selection.getClass().getDeclaredField("placeholders");
        f.setAccessible(true);
        return (List<String>) f.get(selection);
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> sourceChunkIndexes(Object selection) throws Exception {
        Field f = selection.getClass().getDeclaredField("sourceChunkIndexes");
        f.setAccessible(true);
        return (List<Integer>) f.get(selection);
    }

    private static Object newEvidenceImageSelection(List<ModerationLlmAutoRunner.ChunkImageRef> selectedRefs,
                                                    List<Integer> sourceChunkIndexes,
                                                    List<String> placeholders) throws Exception {
        Class<?> clazz = Class.forName("com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner$EvidenceImageSelection");
        Constructor<?> c = clazz.getDeclaredConstructor(List.class, List.class, List.class);
        c.setAccessible(true);
        return c.newInstance(selectedRefs, sourceChunkIndexes, placeholders);
    }

    @Test
    void noEvidenceHit_shouldSelectNoImage_andSendNoImageInput() throws Exception {
        List<ModerationLlmAutoRunner.ChunkImageRef> candidateRefs = List.of(
                new ModerationLlmAutoRunner.ChunkImageRef(1, "[[IMAGE_1]]", "http://x/1.png", "image/png", 11L),
                new ModerationLlmAutoRunner.ChunkImageRef(2, "[[IMAGE_2]]", "http://x/2.png", "image/png", 11L)
        );

        Map<String, Object> mem = Map.of(
                "llmEvidenceByChunk", Map.of(
                        "0", List.of("{\"before_context\":\"plain\",\"after_context\":\"evidence\",\"text\":\"plain text evidence\"}"),
                        "1", List.of("{\"before_context\":\"still\",\"after_context\":\"placeholder\",\"text\":\"still no image placeholder\"}")
                )
        );

        Object selection = select(mem, 2, candidateRefs);
        List<ModerationLlmAutoRunner.ChunkImageRef> selected = selectedRefs(selection);
        List<String> pickedPlaceholders = placeholders(selection);
        List<Integer> sourceChunks = sourceChunkIndexes(selection);

        assertTrue(selected.isEmpty());
        assertTrue(pickedPlaceholders.isEmpty());
        assertTrue(sourceChunks.isEmpty());

        List<LlmModerationTestRequest.ImageInput> inputs = ModerationLlmAutoRunner.refsToImageInputs(selected, 11L);
        assertTrue(inputs.isEmpty());
    }

    @Test
    void partialEvidenceHit_shouldOnlySelectMatchedSubset() throws Exception {
        List<ModerationLlmAutoRunner.ChunkImageRef> candidateRefs = List.of(
                new ModerationLlmAutoRunner.ChunkImageRef(1, "[[IMAGE_1]]", "http://x/1.png", "image/png", 21L),
                new ModerationLlmAutoRunner.ChunkImageRef(2, "[[IMAGE_2]]", "http://x/2.png", "image/png", 21L),
                new ModerationLlmAutoRunner.ChunkImageRef(3, "[[IMAGE_3]]", "http://x/3.png", "image/png", 21L)
        );

        Map<String, Object> mem = Map.of(
                "llmEvidenceByChunk", Map.of(
                        "0", List.of("[[IMAGE_9]]"),
                        "1", List.of("[[IMAGE_2]]", "{\"before_context\":\"plain\",\"after_context\":\"tail\",\"text\":\"plain\"}"),
                        "3", List.of("[[IMAGE_3]]")
                )
        );

        Object selection = select(mem, 3, candidateRefs);
        List<ModerationLlmAutoRunner.ChunkImageRef> selected = selectedRefs(selection);
        List<String> pickedPlaceholders = placeholders(selection);

        assertEquals(1, selected.size());
        assertEquals("[[IMAGE_2]]", selected.get(0).placeholder);
        assertEquals(List.of("[[IMAGE_2]]"), pickedPlaceholders);

        List<LlmModerationTestRequest.ImageInput> inputs = ModerationLlmAutoRunner.refsToImageInputs(selected, 21L);
        assertEquals(1, inputs.size());
        assertEquals("http://x/2.png", inputs.get(0).getUrl());
        assertEquals(21L, inputs.get(0).getFileAssetId());
    }

    @Test
    void integerChunkKey_shouldFallbackLookupByInteger_andTrackSourceChunksInReverseOrder() throws Exception {
        List<ModerationLlmAutoRunner.ChunkImageRef> candidateRefs = List.of(
                new ModerationLlmAutoRunner.ChunkImageRef(1, "[[IMAGE_1]]", "http://x/1.png", "image/png", 31L),
                new ModerationLlmAutoRunner.ChunkImageRef(2, "[[IMAGE_2]]", "http://x/2.png", "image/png", 31L),
                new ModerationLlmAutoRunner.ChunkImageRef(3, null, "http://x/3.png", "image/png", 31L)
        );
        Map<Object, Object> byChunk = new LinkedHashMap<>();
        byChunk.put(5, List.of("[[IMAGE_2]]"));
        byChunk.put("2", List.of("[[IMAGE_1]]"));
        byChunk.put("7", List.of("[[IMAGE_9]]"));
        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("llmEvidenceByChunk", byChunk);

        Object selection = select(mem, 7, candidateRefs);
        List<ModerationLlmAutoRunner.ChunkImageRef> selected = selectedRefs(selection);
        List<String> pickedPlaceholders = placeholders(selection);
        List<Integer> sourceChunks = sourceChunkIndexes(selection);

        assertEquals(2, selected.size());
        assertEquals("[[IMAGE_1]]", selected.get(0).placeholder);
        assertEquals("[[IMAGE_2]]", selected.get(1).placeholder);
        assertEquals(List.of(5, 2), sourceChunks);
        assertEquals(List.of("[[IMAGE_1]]", "[[IMAGE_2]]"), pickedPlaceholders);
    }

    @Test
    void nullChunkIndex_shouldIncludeAllPreviousChunks_andDeduplicatePickedPlaceholders() throws Exception {
        List<ModerationLlmAutoRunner.ChunkImageRef> candidateRefs = java.util.Arrays.asList(
                null,
                new ModerationLlmAutoRunner.ChunkImageRef(8, " [[IMAGE_8]] ", "http://x/8a.png", "image/png", 41L),
                new ModerationLlmAutoRunner.ChunkImageRef(9, "[[IMAGE_8]]", "http://x/8b.png", "image/png", 41L),
                new ModerationLlmAutoRunner.ChunkImageRef(10, " ", "http://x/10.png", "image/png", 41L)
        );
        Map<String, Object> mem = Map.of(
                "llmEvidenceByChunk", Map.of(
                        "0", List.of("[[IMAGE_8]]", "[[IMAGE_8]]"),
                        "1", List.of("[[IMAGE_8]]")
                )
        );

        Object selection = select(mem, null, candidateRefs);
        List<ModerationLlmAutoRunner.ChunkImageRef> selected = selectedRefs(selection);
        List<String> pickedPlaceholders = placeholders(selection);
        List<Integer> sourceChunks = sourceChunkIndexes(selection);

        assertEquals(2, selected.size());
        assertEquals(List.of(1, 0), sourceChunks);
        assertEquals(List.of("[[IMAGE_8]]"), pickedPlaceholders);
    }

    @Test
    void evidenceImageSelectionConstructor_shouldFallbackToEmptyListsWhenNullInputs() throws Exception {
        Object selection = newEvidenceImageSelection(null, null, null);
        assertTrue(selectedRefs(selection).isEmpty());
        assertTrue(sourceChunkIndexes(selection).isEmpty());
        assertTrue(placeholders(selection).isEmpty());
    }

    @Test
    void evidenceImageSelectionConstructor_shouldKeepProvidedListsWhenNonNull() throws Exception {
        List<ModerationLlmAutoRunner.ChunkImageRef> refs = List.of(
                new ModerationLlmAutoRunner.ChunkImageRef(1, "[[IMAGE_1]]", "http://x/1.png", "image/png", 51L)
        );
        List<Integer> sourceChunks = List.of(3, 2);
        List<String> picked = List.of("[[IMAGE_1]]");

        Object selection = newEvidenceImageSelection(refs, sourceChunks, picked);
        assertEquals(refs, selectedRefs(selection));
        assertEquals(sourceChunks, sourceChunkIndexes(selection));
        assertEquals(picked, placeholders(selection));
    }
}
