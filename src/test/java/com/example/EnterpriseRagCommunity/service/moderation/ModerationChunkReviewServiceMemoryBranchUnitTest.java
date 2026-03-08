package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceMemoryBranchUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void normalizeStringList_shouldReturnEmptyWhenInputNotCollection() throws Exception {
        List<String> out = invokeNormalizeStringList("x");
        assertTrue(out.isEmpty());
    }

    @Test
    void normalizeStringList_shouldTrimFilterAndLimitTo200() throws Exception {
        List<Object> raw = new ArrayList<>();
        raw.add(null);
        raw.add(" ");
        raw.add("  first  ");
        for (int i = 0; i < 220; i++) raw.add("v" + i);

        List<String> out = invokeNormalizeStringList(raw);

        assertEquals(200, out.size());
        assertEquals("first", out.get(0));
        assertEquals("v198", out.get(199));
    }

    @Test
    void normalizeListOfMaps_shouldReturnEmptyWhenInputNotCollection() throws Exception {
        List<Map<String, Object>> out = invokeNormalizeListOfMaps(1);
        assertTrue(out.isEmpty());
    }

    @Test
    void normalizeListOfMaps_shouldKeepMapsAndDropNullKeys() throws Exception {
        LinkedHashMap<Object, Object> first = new LinkedHashMap<>();
        first.put("type", "person");
        first.put("value", "alice");
        first.put(null, "ignored");
        LinkedHashMap<Object, Object> second = new LinkedHashMap<>();
        second.put(100, "n");
        second.put("ok", true);

        List<Map<String, Object>> out = invokeNormalizeListOfMaps(List.of(first, "x", second));

        assertEquals(2, out.size());
        assertEquals("person", out.get(0).get("type"));
        assertEquals("alice", out.get(0).get("value"));
        assertFalse(out.get(0).containsKey("null"));
        assertEquals("n", out.get(1).get("100"));
        assertEquals(Boolean.TRUE, out.get(1).get("ok"));
    }

    @Test
    void enforceMemoryMaxChars_shouldDropChunkTextSnippetEntriesWhenOversized() throws Exception {
        LinkedHashMap<String, Object> snippetByChunk = new LinkedHashMap<>();
        snippetByChunk.put("0", "A".repeat(800));
        snippetByChunk.put("1", "B".repeat(800));
        snippetByChunk.put("2", "C".repeat(800));

        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("chunkTextSnippetByChunk", snippetByChunk);
        mem.put("stable", "ok");

        invokeEnforceMemoryMaxChars(mem, 500);
        String json = MAPPER.writeValueAsString(mem);

        assertTrue(json.length() <= 500);
        assertFalse(mem.containsKey("chunkTextSnippetByChunk"));
        assertEquals("ok", mem.get("stable"));
    }

    @Test
    void enforceMemoryMaxChars_shouldTrimEvidenceAndEntitiesWhenOversized() throws Exception {
        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("evidence", new ArrayList<>(List.of("E".repeat(700), "F".repeat(700))));
        mem.put("entities", new ArrayList<>(List.of(
                Map.of("type", "person", "value", "G".repeat(700)),
                Map.of("type", "org", "value", "H".repeat(700))
        )));
        mem.put("stable", "ok");

        invokeEnforceMemoryMaxChars(mem, 500);
        String json = MAPPER.writeValueAsString(mem);

        assertTrue(json.length() <= 500);
        assertFalse(mem.containsKey("evidence"));
        assertFalse(mem.containsKey("entities"));
        assertEquals("ok", mem.get("stable"));
    }

    @Test
    void maybeUpdateMemory_shouldReturnWhenGlobalMemoryDisabled() throws Exception {
        Fixture fx = new Fixture(false);

        invokeMaybeUpdateMemory(fx.service, 9L, Map.of("riskTags", List.of("r1")), 0.3, Verdict.REVIEW);

        verify(fx.chunkSetRepository, never()).findById(any());
        verify(fx.chunkSetRepository, never()).saveAndFlush(any());
    }

    @Test
    void maybeUpdateMemory_shouldMergeAndPersistMemoryFields() throws Exception {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(11L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setMemoryJson(new LinkedHashMap<>(Map.of(
                "riskTags", List.of("old", "dup"),
                "imageRiskTags", List.of("img0"),
                "maxScore", 0.4,
                "entities", List.of(Map.of("type", "person", "value", "alice", "chunkIndex", 0)),
                "openQuestions", List.of("existing-question")
        )));
        when(fx.chunkSetRepository.findById(11L)).thenReturn(Optional.of(set));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setThresholds(Map.of(
                "chunk.memory.maxChars", 5000,
                "chunk.memory.maxEntities", 2,
                "chunk.prevSummary.maxChars", 5
        ));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("riskTags", List.of("dup", "new-a"));
        labels.put("labels", List.of("new-b", " "));
        labels.put("chunkIndex", 7);
        labels.put("summaryForNext", " 1234567 ");
        labels.put("evidence", List.of("line-1", "line-1", "line-2"));
        labels.put("entities", List.of(
                Map.of("type", "person", "value", "alice", "chunkIndex", 1),
                Map.of("type", "org", "value", "acme", "chunkIndex", 7)
        ));

        invokeMaybeUpdateMemory(fx.service, 11L, labels, 0.9, Verdict.REJECT);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertNotNull(mem.get("updatedAt"));
        assertEquals(0.9, ((Number) mem.get("maxScore")).doubleValue(), 0.0001);
        assertEquals("REJECT", mem.get("lastVerdict"));
        assertEquals("12345", mem.get("prevSummary"));
        assertEquals("12345", ((Map<?, ?>) mem.get("summaries")).get("7"));

        List<String> riskTags = toStringList(mem.get("riskTags"));
        assertTrue(riskTags.contains("old"));
        assertTrue(riskTags.contains("img0"));
        assertTrue(riskTags.contains("new-a"));
        assertTrue(riskTags.contains("new-b"));

        Map<?, ?> llmEvidenceByChunk = (Map<?, ?>) mem.get("llmEvidenceByChunk");
        assertTrue(llmEvidenceByChunk.containsKey("7"));
        List<String> evidence = toStringList(llmEvidenceByChunk.get("7"));
        assertEquals(2, evidence.size());

        List<Map<String, Object>> entities = toMapList(mem.get("entities"));
        assertEquals(2, entities.size());
        assertTrue(entities.stream().anyMatch(e -> "person".equals(e.get("type")) && "alice".equals(e.get("value"))));
        assertTrue(entities.stream().anyMatch(e -> "org".equals(e.get("type")) && "acme".equals(e.get("value"))));
    }

    @Test
    void maybeUpdateMemory_shouldShortCircuitWhenSetCancelled() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(12L);
        set.setStatus(ChunkSetStatus.CANCELLED);
        when(fx.chunkSetRepository.findById(12L)).thenReturn(Optional.of(set));

        invokeMaybeUpdateMemory(fx.service, 12L, Map.of("riskTags", List.of("r1")), 0.6, Verdict.REVIEW);

        verify(fx.chunkSetRepository, never()).saveAndFlush(any());
    }

    @Test
    void maybeUpdateMemory_shouldHandleNullLabelsAndNullScore() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(13L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setMemoryJson(new LinkedHashMap<>(Map.of(
                "riskTags", List.of("old"),
                "maxScore", 0.7,
                "llmEvidenceByChunk", new LinkedHashMap<>(Map.of("0", List.of("exists")))
        )));
        when(fx.chunkSetRepository.findById(13L)).thenReturn(Optional.of(set));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        invokeMaybeUpdateMemory(fx.service, 13L, null, null, null);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertEquals(0.7, ((Number) mem.get("maxScore")).doubleValue(), 0.0001);
        assertFalse(mem.containsKey("lastVerdict"));
        assertFalse(mem.containsKey("llmEvidenceByChunk"));
        assertNotNull(mem.get("updatedAt"));
        List<String> riskTags = toStringList(mem.get("riskTags"));
        assertEquals(List.of("old"), riskTags);
    }

    @Test
    void maybeUpdateMemory_shouldKeepWorkingWhenFallbackThresholdLoadFails() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(14L);
        set.setStatus(ChunkSetStatus.RUNNING);
        when(fx.chunkSetRepository.findById(14L)).thenReturn(Optional.of(set));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenThrow(new RuntimeException("boom"));

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("chunkIndex", 3);
        labels.put("riskTags", List.of("fallback-risk"));
        labels.put("evidence", List.of("ev-1"));

        invokeMaybeUpdateMemory(fx.service, 14L, labels, 0.4, Verdict.REVIEW);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertEquals(0.4, ((Number) mem.get("maxScore")).doubleValue(), 0.0001);
        assertEquals("REVIEW", mem.get("lastVerdict"));
        assertTrue(toStringList(mem.get("riskTags")).contains("fallback-risk"));
        Map<?, ?> byChunk = (Map<?, ?>) mem.get("llmEvidenceByChunk");
        assertNotNull(byChunk);
        assertTrue(byChunk.containsKey("3"));
    }

    @Test
    void maybeUpdateMemory_shouldCoverZeroThresholdPathsAndEmptyLabels() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(15L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setMemoryJson(new LinkedHashMap<>(Map.of(
                "entities", List.of(Map.of("type", "person", "value", "alice", "chunkIndex", 0))
        )));
        when(fx.chunkSetRepository.findById(15L)).thenReturn(Optional.of(set));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setThresholds(Map.of(
                "chunk.memory.maxChars", -1,
                "chunk.memory.maxEvidenceItems", 0,
                "chunk.memory.maxEntities", -3,
                "chunk.prevSummary.maxChars", 0
        ));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("chunkIndex", 5);
        labels.put("labels", List.of());
        labels.put("summaryForNext", "abc");
        labels.put("evidence", List.of("ev-1"));
        labels.put("entities", List.of(Map.of("type", "org", "value", "acme", "chunkIndex", 5)));

        invokeMaybeUpdateMemory(fx.service, 15L, labels, 0.2, Verdict.REVIEW);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertEquals("", mem.get("prevSummary"));
        Map<?, ?> summaries = (Map<?, ?>) mem.get("summaries");
        assertNotNull(summaries);
        assertEquals("", summaries.get("5"));
        List<Map<String, Object>> entities = toMapList(mem.get("entities"));
        assertEquals(1, entities.size());
        Map<?, ?> byChunk = (Map<?, ?>) mem.get("llmEvidenceByChunk");
        assertNotNull(byChunk);
        assertTrue(byChunk.containsKey("5"));
    }

    @Test
    void updateImageStageMemory_shouldReturnWhenChunkSetIdNull() {
        Fixture fx = new Fixture(true);

        fx.service.updateImageStageMemory(null, 0.6, List.of("r1"), "desc");

        verify(fx.chunkSetRepository, never()).findById(any());
        verify(fx.chunkSetRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateImageStageMemory_shouldReturnWhenSetCancelled() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(21L);
        set.setStatus(ChunkSetStatus.CANCELLED);
        when(fx.chunkSetRepository.findById(21L)).thenReturn(Optional.of(set));

        fx.service.updateImageStageMemory(21L, 0.8, List.of("x"), "desc");

        verify(fx.chunkSetRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateImageStageMemory_shouldClampScoreMergeTagsAndTrimDescription() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(22L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setMemoryJson(new LinkedHashMap<>(Map.of("riskTags", List.of("old", "dup"))));
        when(fx.chunkSetRepository.findById(22L)).thenReturn(Optional.of(set));

        fx.service.updateImageStageMemory(22L, 1.8, List.of(" dup ", "new", " "), "x".repeat(2200));

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertEquals(1.0, ((Number) mem.get("imageScore")).doubleValue(), 0.0001);
        assertEquals(2000, String.valueOf(mem.get("imageDescription")).length());
        assertNotNull(mem.get("updatedAt"));

        List<String> risk = toStringList(mem.get("riskTags"));
        assertTrue(risk.contains("old"));
        assertTrue(risk.contains("dup"));
        assertTrue(risk.contains("new"));
    }

    @Test
    void updateImageStageMemory_shouldSkipNullScoreEmptyRiskTagsAndBlankDescription() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(23L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setMemoryJson(new LinkedHashMap<>(Map.of(
                "imageScore", 0.4,
                "imageDescription", "old-desc",
                "riskTags", List.of("old")
        )));
        when(fx.chunkSetRepository.findById(23L)).thenReturn(Optional.of(set));

        fx.service.updateImageStageMemory(23L, null, List.of(), "   ");

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertEquals(0.4, ((Number) mem.get("imageScore")).doubleValue(), 0.0001);
        assertEquals("old-desc", mem.get("imageDescription"));
        assertEquals(List.of("old"), toStringList(mem.get("riskTags")));
        assertFalse(mem.containsKey("imageRiskTags"));
        assertNotNull(mem.get("updatedAt"));
    }

    @Test
    void updateImageStageMemory_shouldRetryWhenOptimisticLockingFailure() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(24L);
        set.setStatus(ChunkSetStatus.RUNNING);
        when(fx.chunkSetRepository.findById(24L)).thenReturn(Optional.of(set));
        AtomicInteger counter = new AtomicInteger(0);
        doAnswer(invocation -> {
            int idx = counter.getAndIncrement();
            if (idx < 2) throw new OptimisticLockingFailureException("conflict");
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> cb = (TransactionCallback<Object>) invocation.getArgument(0);
            return cb.doInTransaction(null);
        }).when(fx.txTemplate).execute(any());

        fx.service.updateImageStageMemory(24L, 0.5, List.of("a"), "desc");

        verify(fx.txTemplate, times(3)).execute(any());
        verify(fx.chunkSetRepository).saveAndFlush(set);
    }

    @Test
    void updateImageStageMemory_shouldReturnWhenGeneralExceptionThrown() {
        Fixture fx = new Fixture(true);
        doThrow(new RuntimeException("boom")).when(fx.txTemplate).execute(any());

        fx.service.updateImageStageMemory(25L, 0.7, List.of("a"), "desc");

        verify(fx.txTemplate, times(1)).execute(any());
        verify(fx.chunkSetRepository, never()).findById(any());
        verify(fx.chunkSetRepository, never()).saveAndFlush(any());
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeNormalizeStringList(Object input) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("normalizeStringList", Object.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, input);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> invokeNormalizeListOfMaps(Object input) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("normalizeListOfMaps", Object.class);
        m.setAccessible(true);
        return (List<Map<String, Object>>) m.invoke(null, input);
    }

    private static void invokeEnforceMemoryMaxChars(Map<String, Object> mem, int maxChars) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("enforceMemoryMaxChars", Map.class, int.class);
        m.setAccessible(true);
        m.invoke(null, mem, maxChars);
    }

    private static void invokeMaybeUpdateMemory(ModerationChunkReviewService service, Long chunkSetId, Map<String, Object> labels, Double score, Verdict verdict) {
        ReflectionTestUtils.invokeMethod(service, "maybeUpdateMemory", chunkSetId, labels, score, verdict);
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object v) {
        if (!(v instanceof Collection<?> col)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : col) out.add(String.valueOf(o));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toMapList(Object v) {
        if (!(v instanceof Collection<?> col)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : col) {
            if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
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
        final TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        final ModerationChunkReviewService service;

        Fixture(boolean enableGlobalMemory) {
            ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
            cfg.setEnabled(true);
            cfg.setEnableGlobalMemory(enableGlobalMemory);
            when(configService.getConfig()).thenReturn(cfg);
            when(chunkSetRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(txTemplate.execute(any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                TransactionCallback<Object> cb = (TransactionCallback<Object>) invocation.getArgument(0);
                return cb.doInTransaction(null);
            });

            service = new ModerationChunkReviewService(
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
            ReflectionTestUtils.setField(service, "cachedRequiresNewTx", txTemplate);
        }
    }
}
