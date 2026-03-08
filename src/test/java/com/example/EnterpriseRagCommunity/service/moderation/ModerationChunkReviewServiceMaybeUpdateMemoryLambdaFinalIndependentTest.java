package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceMaybeUpdateMemoryLambdaFinalIndependentTest {

    @Test
    void maybeUpdateMemory_shouldHandleEmptyLabelsAndMutuallyMergeRiskSources() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(71L);
        set.setStatus(ChunkSetStatus.RUNNING);
        ArrayList<Object> imageRiskTags = new ArrayList<>();
        imageRiskTags.add("img-risk");
        imageRiskTags.add(" ");
        imageRiskTags.add(null);
        set.setMemoryJson(new LinkedHashMap<>(Map.of(
                "riskTags", List.of("mem-risk", " "),
                "imageRiskTags", imageRiskTags,
                "llmEvidenceByChunk", new LinkedHashMap<>(Map.of("3", List.of("e-3")))
        )));
        when(fx.chunkSetRepository.findById(71L)).thenReturn(Optional.of(set));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        invokeMaybeUpdateMemory(fx.service, 71L, new LinkedHashMap<>(), null);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        List<String> riskTags = toStringList(mem.get("riskTags"));
        assertTrue(riskTags.contains("mem-risk"));
        assertTrue(riskTags.contains("img-risk"));
        assertEquals(2, riskTags.size());
        assertNotNull(mem.get("updatedAt"));
    }

    @Test
    void maybeUpdateMemory_shouldSkipNullItemsInsideLabelCollection() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(76L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setMemoryJson(new LinkedHashMap<>(Map.of("riskTags", List.of("base"))));
        when(fx.chunkSetRepository.findById(76L)).thenReturn(Optional.of(set));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        ArrayList<Object> labelsList = new ArrayList<>();
        labelsList.add(null);
        labelsList.add("new-risk");
        labelsList.add(" ");
        invokeMaybeUpdateMemory(fx.service, 76L, new LinkedHashMap<>(Map.of("labels", labelsList)), null);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        List<String> riskTags = toStringList(set.getMemoryJson().get("riskTags"));
        assertTrue(riskTags.contains("base"));
        assertTrue(riskTags.contains("new-risk"));
    }

    @Test
    void maybeUpdateMemory_shouldIgnoreNonCollectionRiskAndClipEntitiesWhenMaxExceeded() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(72L);
        set.setStatus(ChunkSetStatus.RUNNING);

        LinkedHashMap<String, Object> byChunk = new LinkedHashMap<>();
        byChunk.put(null, List.of("ignored-null-key"));
        ArrayList<Object> oldChunkEvidence = new ArrayList<>();
        oldChunkEvidence.add("dup-ev");
        oldChunkEvidence.add(" ");
        oldChunkEvidence.add(null);
        byChunk.put("old", oldChunkEvidence);
        byChunk.put("bad-v", "non-collection");
        byChunk.put("ok", List.of("keep-ok"));

        ArrayList<Object> oldEntities = new ArrayList<>();
        oldEntities.add(null);
        oldEntities.add(new LinkedHashMap<>(Map.of("type", "person", "value", "alice", "chunkIndex", 1)));
        oldEntities.add(new LinkedHashMap<>(Map.of("type", "org", "value", "acme", "chunkIndex", 2)));
        oldEntities.add(new LinkedHashMap<>(Map.of("type", "topic", "value", "security", "chunkIndex", 3)));
        oldEntities.add(new LinkedHashMap<>(Map.of("type", "", "value", "bad-type", "chunkIndex", 4)));
        oldEntities.add(new LinkedHashMap<>(Map.of("type", "kw", "value", "", "chunkIndex", 5)));

        LinkedHashMap<String, Object> mem0 = new LinkedHashMap<>();
        mem0.put("riskTags", List.of("base"));
        mem0.put("entities", oldEntities);
        mem0.put("llmEvidenceByChunk", byChunk);
        set.setMemoryJson(mem0);
        when(fx.chunkSetRepository.findById(72L)).thenReturn(Optional.of(set));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setThresholds(Map.of(
                "chunk.memory.maxChars", 200_000,
                "chunk.memory.maxEntities", 2,
                "chunk.prevSummary.maxChars", 50
        ));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));

        ArrayList<Object> incomingEntities = new ArrayList<>();
        incomingEntities.add("non-map");
        incomingEntities.add(new LinkedHashMap<>(Map.of("type", "person", "value", "alice", "chunkIndex", 9)));
        incomingEntities.add(new LinkedHashMap<>(Map.of("type", "event", "value", "release", "chunkIndex", 9)));

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("chunkIndex", 9);
        labels.put("riskTags", "non-collection");
        labels.put("labels", Map.of("bad", "shape"));
        labels.put("evidence", List.of("dup-ev", "dup-ev"));
        labels.put("entities", incomingEntities);

        invokeMaybeUpdateMemory(fx.service, 72L, labels, 0.4);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        List<String> riskTags = toStringList(mem.get("riskTags"));
        assertEquals(List.of("base"), riskTags);

        Map<?, ?> mergedByChunk = (Map<?, ?>) mem.get("llmEvidenceByChunk");
        assertTrue(mergedByChunk.containsKey("old"));
        assertTrue(mergedByChunk.containsKey("ok"));
        assertFalse(mergedByChunk.containsKey("9"));
        assertFalse(mergedByChunk.containsKey("bad-v"));

        List<Map<String, Object>> entities = toMapList(mem.get("entities"));
        assertEquals(2, entities.size());
        assertEquals("person", String.valueOf(entities.get(0).get("type")));
        assertEquals("org", String.valueOf(entities.get(1).get("type")));
    }

    @Test
    void maybeUpdateMemory_shouldEvictSummariesAndEvidenceByChunkAtBoundary() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(73L);
        set.setStatus(ChunkSetStatus.RUNNING);

        LinkedHashMap<String, Object> summaries = new LinkedHashMap<>();
        summaries.put(null, "ignored-null-key");
        summaries.put("ignored-null-value", null);
        summaries.put(" ", "ignored-blank-key");
        for (int i = 0; i < 200; i++) {
            summaries.put("s" + i, "summary-" + i);
        }

        LinkedHashMap<String, Object> byChunk = new LinkedHashMap<>();
        byChunk.put(null, List.of("ignored-null-key"));
        byChunk.put("ignored-null-value", null);
        byChunk.put("ignored-blank-value", List.of(" "));
        for (int i = 0; i < 200; i++) {
            byChunk.put("c" + i, List.of("ev-" + i));
        }

        LinkedHashMap<String, Object> mem0 = new LinkedHashMap<>();
        mem0.put("summaries", summaries);
        mem0.put("llmEvidenceByChunk", byChunk);
        set.setMemoryJson(mem0);
        when(fx.chunkSetRepository.findById(73L)).thenReturn(Optional.of(set));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setThresholds(Map.of(
                "chunk.memory.maxChars", 200_000,
                "chunk.memory.maxEntities", 10,
                "chunk.prevSummary.maxChars", 12
        ));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("chunkIndex", 999);
        labels.put("summaryForNext", "new-summary");
        labels.put("evidence", List.of("new-ev-999"));

        invokeMaybeUpdateMemory(fx.service, 73L, labels, null);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();

        Map<?, ?> mergedSummaries = (Map<?, ?>) mem.get("summaries");
        assertEquals(200, mergedSummaries.size());
        assertFalse(mergedSummaries.containsKey("s0"));
        assertTrue(mergedSummaries.containsKey("s1"));
        assertEquals("new-summary", mergedSummaries.get("999"));

        Map<?, ?> mergedByChunk = (Map<?, ?>) mem.get("llmEvidenceByChunk");
        assertEquals(200, mergedByChunk.size());
        assertFalse(mergedByChunk.containsKey("c0"));
        assertTrue(mergedByChunk.containsKey("c1"));
        assertTrue(mergedByChunk.containsKey("999"));
    }

    @Test
    void maybeUpdateMemory_shouldHandleNullLabelsAndFilterInvalidSummariesAndEvidenceEntries() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(74L);
        set.setStatus(ChunkSetStatus.RUNNING);

        LinkedHashMap<String, Object> summaries = new LinkedHashMap<>();
        summaries.put(null, "null-key");
        summaries.put("v-null", null);
        summaries.put(" ", "blank-key");
        summaries.put("ok", "ok-v");

        ArrayList<Object> longEvidence = new ArrayList<>();
        longEvidence.add(" ");
        for (int i = 0; i < 25; i++) {
            longEvidence.add("ev-long-" + i);
        }

        LinkedHashMap<String, Object> byChunk = new LinkedHashMap<>();
        byChunk.put(null, List.of("ignored"));
        byChunk.put("nv", null);
        byChunk.put("good", longEvidence);

        LinkedHashMap<String, Object> mem0 = new LinkedHashMap<>();
        mem0.put("summaries", summaries);
        mem0.put("llmEvidenceByChunk", byChunk);
        set.setMemoryJson(mem0);
        when(fx.chunkSetRepository.findById(74L)).thenReturn(Optional.of(set));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        invokeMaybeUpdateMemory(fx.service, 74L, null, null);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();

        assertFalse(mem.containsKey("prevSummary"));
        Map<?, ?> mergedSummaries = (Map<?, ?>) mem.get("summaries");
        assertTrue(mergedSummaries.containsKey("ok"));

        Map<?, ?> mergedByChunk = (Map<?, ?>) mem.get("llmEvidenceByChunk");
        assertEquals(1, mergedByChunk.size());
        List<String> goodEvidence = toStringList(mergedByChunk.get("good"));
        assertEquals(20, goodEvidence.size());
        assertEquals("ev-long-0", goodEvidence.get(0));
        assertEquals("ev-long-19", goodEvidence.get(19));
    }

    @Test
    void maybeUpdateMemory_shouldKeepEntityAppendPathWithoutEarlyBreak() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(75L);
        set.setStatus(ChunkSetStatus.RUNNING);

        ArrayList<Object> oldEntities = new ArrayList<>();
        oldEntities.add(new LinkedHashMap<>(Map.of("value", "missing-type", "chunkIndex", 1)));
        oldEntities.add(new LinkedHashMap<>(Map.of("type", "missing-value", "chunkIndex", 2)));
        oldEntities.add(new LinkedHashMap<>(Map.of("type", "person", "value", "alice", "chunkIndex", 3)));

        set.setMemoryJson(new LinkedHashMap<>(Map.of("entities", oldEntities)));
        when(fx.chunkSetRepository.findById(75L)).thenReturn(Optional.of(set));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setThresholds(Map.of(
                "chunk.memory.maxChars", 200_000,
                "chunk.memory.maxEntities", 5,
                "chunk.prevSummary.maxChars", 20
        ));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("chunkIndex", 10);
        labels.put("entities", List.of(
                new LinkedHashMap<>(Map.of("type", "org", "value", "acme", "chunkIndex", 10))
        ));

        invokeMaybeUpdateMemory(fx.service, 75L, labels, null);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        List<Map<String, Object>> entities = toMapList(set.getMemoryJson().get("entities"));
        assertEquals(4, entities.size());
        assertTrue(entities.stream().anyMatch(e -> "person".equals(String.valueOf(e.get("type")))));
        assertTrue(entities.stream().anyMatch(e -> "org".equals(String.valueOf(e.get("type")))));
    }

    private static void invokeMaybeUpdateMemory(ModerationChunkReviewService service, Long chunkSetId, Map<String, Object> labels, Double score) {
        ReflectionTestUtils.invokeMethod(service, "maybeUpdateMemory", chunkSetId, labels, score, null);
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object v) {
        if (!(v instanceof Collection<?> col)) return List.of();
        ArrayList<String> out = new ArrayList<>();
        for (Object o : col) out.add(String.valueOf(o));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toMapList(Object v) {
        if (!(v instanceof Collection<?> col)) return List.of();
        ArrayList<Map<String, Object>> out = new ArrayList<>();
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
