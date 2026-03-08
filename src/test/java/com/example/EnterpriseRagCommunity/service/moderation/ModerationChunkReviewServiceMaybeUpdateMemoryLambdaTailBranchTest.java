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
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceMaybeUpdateMemoryLambdaTailBranchTest {

    @Test
    void maybeUpdateMemory_shouldApplyThresholdTruncationDedupAndEviction() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(31L);
        set.setStatus(ChunkSetStatus.RUNNING);

        LinkedHashMap<String, Object> summaries = new LinkedHashMap<>();
        for (int i = 0; i < 205; i++) summaries.put(String.valueOf(i), "s-" + i);

        LinkedHashMap<String, Object> byChunk = new LinkedHashMap<>();
        byChunk.put("bad", "not-collection");
        for (int i = 0; i < 205; i++) {
            byChunk.put(String.valueOf(i), List.of("keep-" + i));
        }
        byChunk.put("50", List.of("dup-evidence", "dup-evidence", " "));
        byChunk.put(" ", List.of("ignored"));

        set.setMemoryJson(new LinkedHashMap<>(Map.of(
                "riskTags", List.of("base", "dup", " "),
                "imageRiskTags", new ArrayList<>(List.of("img", "dup")),
                "entities", List.of(
                        Map.of("type", "person", "value", "alice", "chunkIndex", 0)
                ),
                "summaries", summaries,
                "llmEvidenceByChunk", byChunk,
                "maxScore", 0.2
        )));
        when(fx.chunkSetRepository.findById(31L)).thenReturn(Optional.of(set));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setThresholds(Map.of(
                "chunk.memory.maxChars", 20_000,
                "chunk.memory.maxEntities", 2,
                "chunk.prevSummary.maxChars", 3
        ));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("chunkIndex", 9);
        labels.put("riskTags", List.of("dup", "new-a", " "));
        labels.put("labels", List.of("new-b", "dup", " "));
        labels.put("summaryForNext", "abcdef");
        ArrayList<String> evidence = new ArrayList<>();
        evidence.add("dup-evidence");
        for (int i = 0; i < 30; i++) evidence.add("new-ev-" + i);
        evidence.add("new-ev-2");
        labels.put("evidence", evidence);
        labels.put("entities", List.of(
                Map.of("type", "person", "value", "alice", "chunkIndex", 2),
                Map.of("type", "org", "value", "acme", "chunkIndex", 9),
                Map.of("type", "topic", "value", "security", "chunkIndex", 9)
        ));

        invokeMaybeUpdateMemory(fx.service, 31L, labels, 0.6, Verdict.REVIEW);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertNotNull(mem.get("updatedAt"));
        assertEquals("REVIEW", mem.get("lastVerdict"));
        assertEquals(0.6, ((Number) mem.get("maxScore")).doubleValue(), 0.0001);

        List<String> riskTags = toStringList(mem.get("riskTags"));
        assertTrue(riskTags.contains("base"));
        assertTrue(riskTags.contains("dup"));
        assertTrue(riskTags.contains("img"));
        assertTrue(riskTags.contains("new-a"));
        assertTrue(riskTags.contains("new-b"));

        assertEquals("abc", mem.get("prevSummary"));
        Map<?, ?> mergedSummaries = (Map<?, ?>) mem.get("summaries");
        assertEquals(200, mergedSummaries.size());
        assertEquals("abc", mergedSummaries.get("9"));
        assertFalse(mergedSummaries.containsKey("0"));

        Map<?, ?> llmEvidenceByChunk = (Map<?, ?>) mem.get("llmEvidenceByChunk");
        assertEquals(200, llmEvidenceByChunk.size());
        assertTrue(llmEvidenceByChunk.containsKey("9"));
        assertFalse(llmEvidenceByChunk.containsKey("0"));
        List<String> chunk9Evidence = toStringList(llmEvidenceByChunk.get("9"));
        assertEquals(20, chunk9Evidence.size());
        assertFalse(chunk9Evidence.contains("dup-evidence"));
        assertEquals("new-ev-0", chunk9Evidence.get(0));

        List<Map<String, Object>> entities = toMapList(mem.get("entities"));
        assertEquals(2, entities.size());
        assertTrue(entities.stream().anyMatch(e -> "person".equals(e.get("type")) && "alice".equals(e.get("value"))));
        assertTrue(entities.stream().anyMatch(e -> "org".equals(e.get("type")) && "acme".equals(e.get("value"))));
        assertFalse(entities.stream().anyMatch(e -> "topic".equals(e.get("type")) && "security".equals(e.get("value"))));
    }

    @Test
    void maybeUpdateMemory_shouldRetryOnOptimisticLockingFailureThenSucceed() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(32L);
        set.setStatus(ChunkSetStatus.RUNNING);
        when(fx.chunkSetRepository.findById(32L)).thenReturn(Optional.of(set));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        AtomicInteger attempts = new AtomicInteger(0);
        doAnswer(invocation -> {
            int idx = attempts.getAndIncrement();
            if (idx < 2) throw new OptimisticLockingFailureException("conflict");
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> cb = (TransactionCallback<Object>) invocation.getArgument(0);
            return cb.doInTransaction(null);
        }).when(fx.txTemplate).execute(any());

        invokeMaybeUpdateMemory(
                fx.service,
                32L,
                new LinkedHashMap<>(Map.of("chunkIndex", 1, "evidence", List.of("ev-1"))),
                0.7,
                Verdict.REJECT
        );

        verify(fx.txTemplate, times(3)).execute(any());
        verify(fx.chunkSetRepository).saveAndFlush(set);
        assertEquals("REJECT", set.getMemoryJson().get("lastVerdict"));
    }

    @Test
    void maybeUpdateMemory_shouldReturnWhenTransactionThrowsGeneralException() {
        Fixture fx = new Fixture(true);
        doThrow(new RuntimeException("boom")).when(fx.txTemplate).execute(any());

        invokeMaybeUpdateMemory(
                fx.service,
                33L,
                new LinkedHashMap<>(Map.of("chunkIndex", 2, "riskTags", List.of("x"))),
                0.4,
                Verdict.REVIEW
        );

        verify(fx.txTemplate, times(1)).execute(any());
        verify(fx.chunkSetRepository, never()).findById(any());
        verify(fx.chunkSetRepository, never()).saveAndFlush(any());
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
