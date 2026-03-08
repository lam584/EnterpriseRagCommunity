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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceMaybeUpdateMemoryLambdaRemainingBranchIndependentTest {

    @Test
    void maybeUpdateMemory_shouldHandleMissingChunkIndexEmptyCollectionsAndNonMapMemoryOpenQuestions() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(61L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setMemoryJson(new LinkedHashMap<>(Map.of(
                "entities", List.of(Map.of("type", "person", "value", "alice", "chunkIndex", 1)),
                "openQuestions", Map.of("bad", "shape"),
                "llmEvidenceByChunk", new LinkedHashMap<>(Map.of("0", List.of("old-0")))
        )));
        when(fx.chunkSetRepository.findById(61L)).thenReturn(Optional.of(set));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setThresholds(Map.of(
                "chunk.memory.maxChars", 200_000,
                "chunk.memory.maxEntities", 5,
                "chunk.prevSummary.maxChars", 6
        ));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("summaryForNext", "abcdefghi");
        labels.put("entities", List.of());
        labels.put("evidence", List.of());

        invokeMaybeUpdateMemory(fx.service, 61L, labels, null);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertEquals("abcdef", mem.get("prevSummary"));
        Map<?, ?> summaries = (Map<?, ?>) mem.get("summaries");
        assertEquals(1, summaries.size());
        assertEquals("abcdef", summaries.get("0"));
        assertTrue(mem.get("openQuestions") instanceof Map<?, ?>);
        assertFalse(mem.containsKey("llmEvidenceByChunk"));
        assertEquals(1, toMapList(mem.get("entities")).size());
    }

    @Test
    void maybeUpdateMemory_shouldRespectEvidenceDedupBoundaryAtTwentyItems() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(62L);
        set.setStatus(ChunkSetStatus.RUNNING);

        ArrayList<String> oldEvidence = new ArrayList<>();
        for (int i = 0; i < 19; i++) oldEvidence.add("old-" + i);
        LinkedHashMap<String, Object> byChunk = new LinkedHashMap<>();
        byChunk.put("old", oldEvidence);
        set.setMemoryJson(new LinkedHashMap<>(Map.of("llmEvidenceByChunk", byChunk)));
        when(fx.chunkSetRepository.findById(62L)).thenReturn(Optional.of(set));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setThresholds(Map.of(
                "chunk.memory.maxChars", 200_000,
                "chunk.memory.maxEntities", 5,
                "chunk.prevSummary.maxChars", 6
        ));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));

        ArrayList<String> evidence = new ArrayList<>();
        for (int i = 0; i < 10; i++) evidence.add("old-" + i);
        for (int i = 0; i < 30; i++) evidence.add("new-" + i);
        evidence.add("new-2");
        evidence.add(" ");

        invokeMaybeUpdateMemory(fx.service, 62L, new LinkedHashMap<>(Map.of("chunkIndex", 9, "evidence", evidence)), 0.2);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<?, ?> mergedByChunk = (Map<?, ?>) set.getMemoryJson().get("llmEvidenceByChunk");
        List<String> chunk9Evidence = toStringList(mergedByChunk.get("9"));
        assertEquals(20, chunk9Evidence.size());
        assertEquals("new-0", chunk9Evidence.get(0));
        assertTrue(chunk9Evidence.contains("new-19"));
        assertFalse(chunk9Evidence.contains("new-20"));
        assertEquals(1, chunk9Evidence.stream().filter("new-2"::equals).count());
    }

    @Test
    void maybeUpdateMemory_shouldKeepOpenQuestionsOnlyFromMemorySource() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(63L);
        set.setStatus(ChunkSetStatus.RUNNING);

        ArrayList<Object> openQuestions = new ArrayList<>();
        openQuestions.add("  from-mem  ");
        openQuestions.add(" ");
        openQuestions.add(null);
        set.setMemoryJson(new LinkedHashMap<>(Map.of("openQuestions", openQuestions)));
        when(fx.chunkSetRepository.findById(63L)).thenReturn(Optional.of(set));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("chunkIndex", 3);
        labels.put("openQuestions", List.of("from-label"));

        invokeMaybeUpdateMemory(fx.service, 63L, labels, null);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        List<String> mergedQuestions = toStringList(set.getMemoryJson().get("openQuestions"));
        assertEquals(List.of("from-mem"), mergedQuestions);
        assertFalse(mergedQuestions.contains("from-label"));
    }

    @Test
    void maybeUpdateMemory_shouldEvictOldestSummaryWhenSummariesExceedLimit() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(64L);
        set.setStatus(ChunkSetStatus.RUNNING);

        LinkedHashMap<String, Object> summaries = new LinkedHashMap<>();
        for (int i = 0; i < 200; i++) summaries.put("k" + i, "v" + i);
        set.setMemoryJson(new LinkedHashMap<>(Map.of("summaries", summaries)));
        when(fx.chunkSetRepository.findById(64L)).thenReturn(Optional.of(set));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setThresholds(Map.of(
                "chunk.memory.maxChars", 200_000,
                "chunk.memory.maxEntities", 5,
                "chunk.prevSummary.maxChars", 20
        ));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));

        invokeMaybeUpdateMemory(fx.service, 64L, new LinkedHashMap<>(Map.of("chunkIndex", 999, "summaryForNext", "new-summary")), null);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<?, ?> mergedSummaries = (Map<?, ?>) set.getMemoryJson().get("summaries");
        assertEquals(200, mergedSummaries.size());
        assertFalse(mergedSummaries.containsKey("k0"));
        assertTrue(mergedSummaries.containsKey("k1"));
        assertEquals("new-summary", mergedSummaries.get("999"));
        assertEquals("k1", mergedSummaries.keySet().iterator().next());
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
