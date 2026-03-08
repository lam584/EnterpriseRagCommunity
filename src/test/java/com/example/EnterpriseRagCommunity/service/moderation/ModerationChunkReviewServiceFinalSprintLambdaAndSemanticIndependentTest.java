package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceFinalSprintLambdaAndSemanticIndependentTest {

    @Test
    void maybeUpdateMemory_shouldSkipNullEntriesFromNestedMaps() {
        Fixture fx = new Fixture();
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(801L);
        set.setStatus(ChunkSetStatus.RUNNING);

        NullEntryMap<String, Object> summaries = new NullEntryMap<>();
        summaries.put("m1", "v1");
        summaries.put(" ", "ignored");

        NullEntryMap<String, Object> byChunk = new NullEntryMap<>();
        byChunk.put("c1", List.of("ev-1", " "));
        byChunk.put(" ", List.of("ignored"));

        LinkedHashMap<String, Object> mem0 = new LinkedHashMap<>();
        mem0.put("summaries", summaries);
        mem0.put("llmEvidenceByChunk", byChunk);
        set.setMemoryJson(mem0);

        when(fx.chunkSetRepository.findById(801L)).thenReturn(Optional.of(set));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        invokeMaybeUpdateMemory(fx.service, 801L, new LinkedHashMap<>(Map.of(
                "chunkIndex", 7,
                "summaryForNext", "new-summary",
                "evidence", List.of("ev-2")
        )));

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();

        Map<?, ?> mergedSummaries = (Map<?, ?>) mem.get("summaries");
        assertTrue(mergedSummaries.containsKey("m1"));
        assertEquals("new-summary", mergedSummaries.get("7"));

        Map<?, ?> mergedByChunk = (Map<?, ?>) mem.get("llmEvidenceByChunk");
        assertTrue(mergedByChunk.containsKey("c1"));
        assertTrue(mergedByChunk.containsKey("7"));
        assertFalse(mergedByChunk.containsKey(" "));
    }

    @Test
    void maybeUpdateMemory_shouldMergeSummaryAndEvidenceAndEntityBranchesInSingleFlow() {
        Fixture fx = new Fixture();
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(804L);
        set.setStatus(ChunkSetStatus.RUNNING);

        NullEntryMap<String, Object> summaries = new NullEntryMap<>();
        summaries.put("old", "v-old");
        summaries.put(" ", "ignored");
        summaries.put("drop-null-value", null);

        NullEntryMap<String, Object> byChunk = new NullEntryMap<>();
        byChunk.put("0", List.of(" ", "{\"text\":\"alpha\"}", "{\"text\":\"alpha\"}"));
        byChunk.put("bad-shape", "x");
        byChunk.put(" ", List.of("ignored"));

        ArrayList<Map<String, Object>> entities = new ArrayList<>();
        entities.add(new LinkedHashMap<>(Map.of("type", "person", "value", "alice", "chunkIndex", 1)));
        entities.add(new LinkedHashMap<>(Map.of("type", " ", "value", "invalid", "chunkIndex", 2)));

        LinkedHashMap<String, Object> mem0 = new LinkedHashMap<>();
        mem0.put("summaries", summaries);
        mem0.put("llmEvidenceByChunk", byChunk);
        mem0.put("entities", entities);
        set.setMemoryJson(mem0);
        when(fx.chunkSetRepository.findById(804L)).thenReturn(Optional.of(set));
        when(fx.fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        LinkedHashMap<String, Object> labels = new LinkedHashMap<>();
        labels.put("chunkIndex", 6);
        labels.put("summaryForNext", "summary-next");
        labels.put("evidence", List.of(" ", "{\"before_context\":\"A\",\"after_context\":\"B\"}", "{\"before_context\":\"A\",\"after_context\":\"B\"}", "plain-evidence"));
        labels.put("entities", List.of(
                Map.of("type", "person", "value", "alice", "chunkIndex", 6),
                Map.of("type", "org", "value", "acme", "chunkIndex", 6),
                "non-map-entity"
        ));

        invokeMaybeUpdateMemory(fx.service, 804L, labels);

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();

        Map<?, ?> mergedSummaries = (Map<?, ?>) mem.get("summaries");
        assertEquals("summary-next", mergedSummaries.get("6"));
        assertTrue(mergedSummaries.containsKey("old"));
        assertFalse(mergedSummaries.containsKey(" "));

        Map<?, ?> mergedByChunk = (Map<?, ?>) mem.get("llmEvidenceByChunk");
        assertTrue(mergedByChunk.containsKey("0"));
        assertTrue(mergedByChunk.containsKey("6"));
        List<?> chunk6Evidence = (List<?>) mergedByChunk.get("6");
        assertTrue(chunk6Evidence.size() >= 1);
        assertTrue(chunk6Evidence.size() <= 2);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mergedEntities = (List<Map<String, Object>>) mem.get("entities");
        assertTrue(mergedEntities.stream().anyMatch(e -> "person".equals(String.valueOf(e.get("type")))));
        assertTrue(mergedEntities.stream().anyMatch(e -> "org".equals(String.valueOf(e.get("type")))));
    }

    @Test
    void chunkSemantic_shouldStopAtExactMaxChunksBoundary() throws Exception {
        String text = "b".repeat(2100);
        List<?> spans = invokeChunkSemantic(text, 500, 0, 3);

        assertEquals(3, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(500, spanEnd(spans.get(0)));
        assertEquals(500, spanStart(spans.get(1)));
        assertEquals(1000, spanEnd(spans.get(1)));
        assertEquals(1000, spanStart(spans.get(2)));
        assertEquals(1500, spanEnd(spans.get(2)));
    }

    @Test
    void chunkSemantic_shouldPreserveTrailingSentenceSeparatorInFinalSpan() throws Exception {
        String text = "a".repeat(499) + ".";
        List<?> spans = invokeChunkSemantic(text, 500, 0, 10);

        assertEquals(1, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(text.length(), spanEnd(spans.get(0)));
    }

    @Test
    void chunkSemantic_shouldCoverHardCutSemanticBreakAndOverlapClampBranches() throws Exception {
        String text = "a".repeat(520) + "." + "b".repeat(530) + "." + "c".repeat(540);
        List<?> spans = invokeChunkSemantic(text, 500, 1000, 6);

        assertTrue(spans.size() >= 3);
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(500, spanEnd(spans.get(0)));
        assertTrue(spans.stream().anyMatch(s -> spanEndUnchecked(s) - spanStartUnchecked(s) == 500));
        assertTrue(spans.stream().anyMatch(s -> {
            int end = spanEndUnchecked(s);
            if (end <= 0 || end > text.length()) return false;
            char c = text.charAt(end - 1);
            return c == '.' || c == '?' || c == '!' || c == '。' || c == '？' || c == '！' || c == ';' || c == '\n' || c == '\r';
        }));
    }

    @Test
    void maybeUpdateMemory_shouldStopAfterThreeOptimisticLockFailures() {
        Fixture fx = new Fixture();
        doThrow(new OptimisticLockingFailureException("conflict")).when(fx.txTemplate).execute(any());

        invokeMaybeUpdateMemory(fx.service, 802L, new LinkedHashMap<>(Map.of("chunkIndex", 1)));

        verify(fx.txTemplate, times(3)).execute(any());
        verify(fx.chunkSetRepository, never()).saveAndFlush(any());
    }

    @Test
    void maybeUpdateMemory_shouldReturnWhenTxTemplateReturnsNullResult() {
        Fixture fx = new Fixture();
        doReturn(null).when(fx.txTemplate).execute(any());

        invokeMaybeUpdateMemory(fx.service, 803L, new LinkedHashMap<>(Map.of("chunkIndex", 2)));

        verify(fx.txTemplate, times(1)).execute(any());
        verify(fx.chunkSetRepository, never()).findById(any());
        verify(fx.chunkSetRepository, never()).saveAndFlush(any());
    }

    private static void invokeMaybeUpdateMemory(ModerationChunkReviewService service, Long chunkSetId, Map<String, Object> labels) {
        ReflectionTestUtils.invokeMethod(service, "maybeUpdateMemory", chunkSetId, labels, null, null);
    }

    @SuppressWarnings("unchecked")
    private static List<?> invokeChunkSemantic(String text, Integer chunkSizeChars, Integer overlapChars, Integer maxChunksTotal) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("chunkSemantic", String.class, Integer.class, Integer.class, Integer.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(null, text, chunkSizeChars, overlapChars, maxChunksTotal);
    }

    private static int spanStart(Object span) throws Exception {
        Method m = span.getClass().getDeclaredMethod("start");
        m.setAccessible(true);
        return (Integer) m.invoke(span);
    }

    private static int spanEnd(Object span) throws Exception {
        Method m = span.getClass().getDeclaredMethod("end");
        m.setAccessible(true);
        return (Integer) m.invoke(span);
    }

    private static int spanEndUnchecked(Object span) {
        try {
            return spanEnd(span);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int spanStartUnchecked(Object span) {
        try {
            return spanStart(span);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class NullEntryMap<K, V> extends LinkedHashMap<K, V> {
        @Override
        public Set<Map.Entry<K, V>> entrySet() {
            LinkedHashSet<Map.Entry<K, V>> out = new LinkedHashSet<>();
            out.add(null);
            out.addAll(super.entrySet());
            return out;
        }
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

        Fixture() {
            ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
            cfg.setEnabled(true);
            cfg.setEnableGlobalMemory(true);
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
