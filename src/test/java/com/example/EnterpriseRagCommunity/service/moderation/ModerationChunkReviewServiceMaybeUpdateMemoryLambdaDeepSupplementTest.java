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

class ModerationChunkReviewServiceMaybeUpdateMemoryLambdaDeepSupplementTest {

    @Test
    void maybeUpdateMemory_shouldHandleNonCollectionLabelsAndRiskObjects() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(41L);
        set.setStatus(ChunkSetStatus.RUNNING);

        LinkedHashMap<String, Object> mem0 = new LinkedHashMap<>();
        mem0.put("riskTags", new LinkedHashMap<>());
        mem0.put("imageRiskTags", new LinkedHashMap<>());
        mem0.put("maxScore", 0.2);
        set.setMemoryJson(mem0);
        when(fx.chunkSetRepository.findById(41L)).thenReturn(Optional.of(set));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("chunkIndex", 1);
        labels.put("riskTags", Map.of("x", "y"));
        labels.put("labels", "not-collection");

        invokeMaybeUpdateMemory(fx.service, 41L, labels, 0.3, Verdict.REVIEW);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertTrue(mem.get("riskTags") instanceof Map<?, ?>);
        assertTrue(mem.get("imageRiskTags") instanceof Map<?, ?>);
        assertEquals("REVIEW", mem.get("lastVerdict"));
        assertEquals(0.3, ((Number) mem.get("maxScore")).doubleValue(), 0.0001);
        assertNotNull(mem.get("updatedAt"));
    }

    @Test
    void maybeUpdateMemory_shouldTrimOpenQuestionsEvictMapsAndFilterEntityAndEvidenceBranches() {
        Fixture fx = new Fixture(true);
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(42L);
        set.setStatus(ChunkSetStatus.RUNNING);

        LinkedHashMap<String, Object> summaries = new LinkedHashMap<>();
        for (int i = 0; i < 205; i++) {
            summaries.put(String.valueOf(i), "s-" + i);
        }
        summaries.put(" ", "ignored");
        summaries.put("bad-v", " ");

        LinkedHashMap<String, Object> llmEvidenceByChunk = new LinkedHashMap<>();
        llmEvidenceByChunk.put("x", "not-collection");
        for (int i = 0; i < 205; i++) {
            llmEvidenceByChunk.put(String.valueOf(i), List.of("ev-" + i));
        }
        llmEvidenceByChunk.put(" ", List.of("ignored"));

        ArrayList<String> openQuestions = new ArrayList<>();
        openQuestions.add(" ");
        for (int i = 0; i < 220; i++) {
            openQuestions.add(" q-" + i + " ");
        }

        LinkedHashMap<String, Object> mem0 = new LinkedHashMap<>();
        mem0.put("openQuestions", openQuestions);
        mem0.put("summaries", summaries);
        mem0.put("llmEvidenceByChunk", llmEvidenceByChunk);
        mem0.put("entities", List.of(Map.of("type", "person", "value", "alice", "chunkIndex", 0)));
        set.setMemoryJson(mem0);
        when(fx.chunkSetRepository.findById(42L)).thenReturn(Optional.of(set));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setThresholds(Map.of(
                "chunk.memory.maxChars", 200_000,
                "chunk.memory.maxEntities", 2,
                "chunk.prevSummary.maxChars", 4
        ));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("chunkIndex", 250);
        labels.put("summaryForNext", "summary-xyz");
        labels.put("evidence", List.of(" [[IMAGE_1]] ", "[[IMAGE_2]]", "[[IMAGE_1]]"));
        labels.put("entities", List.of(
                Map.of("value", "no-type", "chunkIndex", 1),
                Map.of("type", "org", "chunkIndex", 1),
                Map.of("type", "org", "value", "acme", "chunkIndex", 250)
        ));

        invokeMaybeUpdateMemory(fx.service, 42L, labels, 0.6, Verdict.REJECT);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertEquals("summ", mem.get("prevSummary"));
        assertEquals("REJECT", mem.get("lastVerdict"));

        List<String> mergedQuestions = toStringList(mem.get("openQuestions"));
        assertEquals(200, mergedQuestions.size());
        assertEquals("q-0", mergedQuestions.get(0));
        assertEquals("q-199", mergedQuestions.get(199));

        Map<?, ?> mergedSummaries = (Map<?, ?>) mem.get("summaries");
        assertEquals(200, mergedSummaries.size());
        assertFalse(mergedSummaries.containsKey("0"));
        assertEquals("summ", mergedSummaries.get("250"));

        Map<?, ?> byChunk = (Map<?, ?>) mem.get("llmEvidenceByChunk");
        assertEquals(200, byChunk.size());
        assertFalse(byChunk.containsKey("0"));
        assertTrue(byChunk.containsKey("250"));
        List<String> chunkEvidence = toStringList(byChunk.get("250"));
        assertEquals(1, chunkEvidence.size());
        assertEquals("[[IMAGE_1]]", chunkEvidence.get(0));

        List<Map<String, Object>> entities = toMapList(mem.get("entities"));
        assertEquals(2, entities.size());
        assertTrue(entities.stream().anyMatch(e -> "person".equals(e.get("type")) && "alice".equals(e.get("value"))));
        assertTrue(entities.stream().anyMatch(e -> "org".equals(e.get("type")) && "acme".equals(e.get("value"))));
    }

    private static void invokeMaybeUpdateMemory(ModerationChunkReviewService service, Long chunkSetId, Map<String, Object> labels, Double score, Verdict verdict) {
        ReflectionTestUtils.invokeMethod(service, "maybeUpdateMemory", chunkSetId, labels, score, verdict);
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object v) {
        if (!(v instanceof Collection<?> col)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : col) {
            out.add(String.valueOf(o));
        }
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
