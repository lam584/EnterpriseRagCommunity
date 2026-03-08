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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceMaybeUpdateMemoryLambdaGapSupplementTest {

    @Test
    void maybeUpdateMemory_shouldReturnWithoutPersistWhenSetMissingOrCancelled() {
        Fixture fx = new Fixture(true);
        when(fx.chunkSetRepository.findById(51L)).thenReturn(Optional.empty());

        ModerationChunkSetEntity cancelled = new ModerationChunkSetEntity();
        cancelled.setId(52L);
        cancelled.setStatus(ChunkSetStatus.CANCELLED);
        when(fx.chunkSetRepository.findById(52L)).thenReturn(Optional.of(cancelled));

        invokeMaybeUpdateMemory(fx.service, 51L, new LinkedHashMap<>(Map.of("chunkIndex", 1)), 0.6);
        invokeMaybeUpdateMemory(fx.service, 52L, new LinkedHashMap<>(Map.of("chunkIndex", 2)), 0.8);

        verify(fx.chunkSetRepository, never()).saveAndFlush(any());
    }

    @Test
    void maybeUpdateMemory_shouldCoverRiskEvidenceAndEntityGateBranches() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(53L);
        set.setStatus(ChunkSetStatus.RUNNING);

        ArrayList<Object> riskTags = new ArrayList<>();
        riskTags.add(null);
        riskTags.add(" ");
        riskTags.add("base");
        ArrayList<Object> imageRiskTags = new ArrayList<>();
        imageRiskTags.add(" ");
        imageRiskTags.add("img");
        LinkedHashMap<String, Object> byChunk = new LinkedHashMap<>();
        byChunk.put(null, List.of("null-key"));
        byChunk.put("n1", null);
        byChunk.put("n2", List.of(" "));
        byChunk.put("0", List.of("old-0"));
        byChunk.put("n3", List.of("ok"));

        LinkedHashMap<String, Object> mem0 = new LinkedHashMap<>();
        mem0.put("riskTags", riskTags);
        mem0.put("imageRiskTags", imageRiskTags);
        mem0.put("entities", List.of(
                Map.of("type", "person", "value", "alice", "chunkIndex", 0)
        ));
        mem0.put("openQuestions", Map.of("x", "y"));
        mem0.put("llmEvidenceByChunk", byChunk);
        set.setMemoryJson(mem0);
        when(fx.chunkSetRepository.findById(53L)).thenReturn(Optional.of(set));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setThresholds(Map.of(
                "chunk.memory.maxChars", 200_000,
                "chunk.memory.maxEntities", 0,
                "chunk.prevSummary.maxChars", 5
        ));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));

        ArrayList<Object> mergedRiskFromLabels = new ArrayList<>();
        mergedRiskFromLabels.add(null);
        mergedRiskFromLabels.add(" ");
        mergedRiskFromLabels.add("new");
        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("chunkIndex", 0);
        labels.put("riskTags", mergedRiskFromLabels);
        labels.put("labels", "not-collection");
        labels.put("evidence", Map.of("x", "y"));
        labels.put("entities", List.of(Map.of("type", "org", "value", "acme", "chunkIndex", 9)));

        invokeMaybeUpdateMemory(fx.service, 53L, labels, null);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertNotNull(mem.get("updatedAt"));
        assertFalse(mem.containsKey("prevSummary"));
        assertFalse(mem.containsKey("lastVerdict"));
        assertFalse(mem.containsKey("maxScore"));
        assertFalse(toMapList(mem.get("entities")).stream().anyMatch(e -> "org".equals(e.get("type"))));

        List<String> mergedRisk = toStringList(mem.get("riskTags"));
        assertTrue(mergedRisk.contains("base"));
        assertTrue(mergedRisk.contains("img"));
        assertTrue(mergedRisk.contains("new"));

        Map<?, ?> mergedByChunk = (Map<?, ?>) mem.get("llmEvidenceByChunk");
        assertTrue(mergedByChunk.containsKey("n3"));
        assertFalse(mergedByChunk.containsKey("0"));
    }

    @Test
    void maybeUpdateMemory_shouldIgnoreNonMapEntitiesAndBlankEvidenceItems() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(54L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setMemoryJson(new LinkedHashMap<>(Map.of("entities", List.of())));
        when(fx.chunkSetRepository.findById(54L)).thenReturn(Optional.of(set));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setThresholds(Map.of(
                "chunk.memory.maxChars", 200_000,
                "chunk.memory.maxEntities", 5,
                "chunk.prevSummary.maxChars", 5
        ));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));

        ArrayList<Object> entities = new ArrayList<>();
        entities.add("non-map");
        entities.add(Map.of("type", "", "value", "v", "chunkIndex", 1));
        entities.add(Map.of("type", "person", "value", "", "chunkIndex", 1));

        ArrayList<Object> evidence = new ArrayList<>();
        evidence.add(null);
        evidence.add(" ");
        evidence.add("[[IMAGE_1]]");
        evidence.add("[[IMAGE_1]]");

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("chunkIndex", 4);
        labels.put("entities", entities);
        labels.put("evidence", evidence);

        invokeMaybeUpdateMemory(fx.service, 54L, labels, 0.1);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertEquals(0.1, ((Number) mem.get("maxScore")).doubleValue(), 0.0001);
        Map<?, ?> byChunk = (Map<?, ?>) mem.get("llmEvidenceByChunk");
        List<String> chunkEvidence = toStringList(byChunk.get("4"));
        assertEquals(1, chunkEvidence.size());
        assertEquals("[[IMAGE_1]]", chunkEvidence.get(0));
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
