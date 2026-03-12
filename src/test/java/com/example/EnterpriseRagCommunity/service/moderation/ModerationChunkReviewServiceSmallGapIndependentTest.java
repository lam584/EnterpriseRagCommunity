package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
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

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceSmallGapIndependentTest {

    @Test
    void getProgress_shouldThrowWhenQueueIdIsNull() {
        Fixture fx = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> fx.service.getProgress(null, true, 10));
    }

    @Test
    void getProgress_shouldReturnEmptyChunksWhenRepositoryReturnsNullList() {
        Fixture fx = new Fixture();
        ModerationChunkSetEntity set = chunkSet(1001L, ContentType.POST, ChunkSetStatus.RUNNING, 2, 501L);
        when(fx.chunkSetRepository.findByQueueId(501L)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(org.mockito.ArgumentMatchers.eq(1001L), any())).thenReturn(0L);
        when(fx.chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(1001L)).thenReturn(null);

        AdminModerationChunkProgressDTO out = fx.service.getProgress(501L, true, 5);

        assertNotNull(out.getChunks());
        assertTrue(out.getChunks().isEmpty());
    }

    @Test
    void loadChunkText_shouldHandleNullPostAndExtractionText() {
        Fixture fx = new Fixture();
        ModerationChunkSetEntity set = chunkSet(1002L, ContentType.POST, ChunkSetStatus.RUNNING, 0, 502L);
        set.setContentId(9001L);
        when(fx.chunkSetRepository.findByQueueId(502L)).thenReturn(Optional.of(set));

        PostsEntity post = new PostsEntity();
        post.setId(9001L);
        post.setContent(null);
        when(fx.postsRepository.findById(9001L)).thenReturn(Optional.of(post));

        assertEquals("", fx.service.loadChunkText(502L, ChunkSourceType.POST_TEXT, null, 0, 10).orElse("x"));
        assertTrue(fx.service.loadChunkText(502L, null, null, 0, 10).isEmpty());

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(8001L);
        ex.setExtractedText(null);
        when(fx.fileAssetExtractionsRepository.findById(8001L)).thenReturn(Optional.of(ex));
        assertEquals("", fx.service.loadChunkText(502L, ChunkSourceType.FILE_TEXT, 8001L, 0, 10).orElse("x"));
    }

    @Test
    void listEligibleChunks_shouldUseDefaultMaxAttemptsWhenConfigNullOrValueNull() {
        Fixture fx = new Fixture();
        ModerationChunkEntity failed2 = chunk(7001L, ChunkStatus.FAILED, 2);
        when(fx.chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(700L)).thenReturn(List.of(failed2));

        when(fx.configService.getConfig()).thenReturn(null);
        List<ModerationChunkReviewService.ChunkCandidate> out1 = fx.service.listEligibleChunks(700L);
        assertEquals(1, out1.size());
        assertEquals(2, out1.get(0).attempts());

        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setMaxAttempts(null);
        when(fx.configService.getConfig()).thenReturn(cfg);
        List<ModerationChunkReviewService.ChunkCandidate> out2 = fx.service.listEligibleChunks(700L);
        assertEquals(1, out2.size());
        assertEquals(7001L, out2.get(0).chunkId());
    }

    @Test
    void listEligibleChunks_shouldReturnEmptyWhenRepositoryReturnsNull() {
        Fixture fx = new Fixture();
        when(fx.chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(701L)).thenReturn(null);
        assertTrue(fx.service.listEligibleChunks(701L).isEmpty());
    }

    @Test
    void chunkSemantic_shouldUseDefaultChunkSizeAndNoOverlapWhenParamsNull() throws Exception {
        String text = "z".repeat(6000);
        List<?> spans = invokeChunkSemantic(text, null, null, null);
        assertEquals(2, spans.size());
        assertEquals(0, spanStart(spans.get(0)));
        assertEquals(4000, spanEnd(spans.get(0)));
        assertEquals(4000, spanStart(spans.get(1)));
        assertEquals(6000, spanEnd(spans.get(1)));
    }

    @Test
    void updateImageStageMemory_shouldHandleSetMissingAndNormalizeImageRiskTags() {
        Fixture fx = new Fixture();
        fx.service.updateImageStageMemory(3001L, 0.4, List.of("x"), "d");
        verify(fx.chunkSetRepository, never()).saveAndFlush(any());

        ModerationChunkSetEntity set = chunkSet(3002L, ContentType.POST, ChunkSetStatus.RUNNING, 0, 9002L);
        set.setMemoryJson(new LinkedHashMap<>(Map.of("riskTags", "not-collection")));
        when(fx.chunkSetRepository.findById(3002L)).thenReturn(Optional.of(set));

        fx.service.updateImageStageMemory(3002L, null, Arrays.asList(null, " ", "r1"), "  ");

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertEquals(List.of("r1"), mem.get("riskTags"));
        assertFalse(mem.containsKey("imageDescription"));
        assertNotNull(mem.get("updatedAt"));
    }

    @Test
    void claimChunkById_shouldUseDefaultMaxAttemptsWhenConfigIsNull() {
        Fixture fx = new Fixture();
        when(fx.configService.getConfig()).thenReturn(null);
        ModerationChunkEntity c = chunk(3101L, ChunkStatus.FAILED, null);
        when(fx.chunkRepository.findByIdForUpdate(3101L)).thenReturn(List.of(c));

        Optional<ModerationChunkReviewService.ChunkToProcess> out = fx.service.claimChunkById(3101L);

        assertTrue(out.isPresent());
        assertEquals(3101L, out.get().chunkId());
        assertEquals(ChunkStatus.RUNNING, c.getStatus());
        assertEquals(1, c.getAttempts());
        verify(fx.chunkRepository).save(c);
    }

    @Test
    void maybeUpdateMemory_shouldReturnImmediatelyWhenChunkSetIdNullOrConfigNull() throws Exception {
        Fixture fx = new Fixture();

        invokeMaybeUpdateMemory(fx.service, null, new LinkedHashMap<>(), 0.2, Verdict.REVIEW);
        verify(fx.configService, never()).getConfig();

        when(fx.configService.getConfig()).thenReturn(null);
        invokeMaybeUpdateMemory(fx.service, 3201L, new LinkedHashMap<>(), 0.3, Verdict.REJECT);
        verify(fx.chunkSetRepository, never()).findById(any());
        verify(fx.chunkSetRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolveChunkSizingDecision_shouldClampChunkSizeAndSkipPromptLookupWhenPromptCodeNull() throws Exception {
        Fixture fx = new Fixture();
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setChunkSizeChars(100);

        ModerationLlmConfigEntity llm = new ModerationLlmConfigEntity();
        llm.setMultimodalPromptCode(null);
        when(fx.llmConfigRepository.findAll()).thenReturn(List.of(llm));

        Object decision = invokeResolveChunkSizingDecision(fx.service, cfg);
        Map<String, Object> log = budgetLog(decision);

        assertEquals(500, effectiveChunkSizeChars(decision));
        assertEquals(500, ((Number) log.get("baseChunkSizeChars")).intValue());
        assertFalse((Boolean) log.get("triggeredResharding"));
        verify(fx.promptsRepository, never()).findByPromptCode(any());
    }

    @Test
    void loadProgressSummaries_shouldReturnEmptyWhenRepositoryReturnsNullOrEmpty() {
        Fixture fx = new Fixture();
        when(fx.chunkSetRepository.findAllByQueueIds(List.of(3301L))).thenReturn(null);
        assertEquals(Map.of(), fx.service.loadProgressSummaries(List.of(3301L)));

        when(fx.chunkSetRepository.findAllByQueueIds(List.of(3302L))).thenReturn(List.of());
        assertEquals(Map.of(), fx.service.loadProgressSummaries(List.of(3302L)));
    }

    @SuppressWarnings("unchecked")
    private static List<?> invokeChunkSemantic(String text, Integer chunkSizeChars, Integer overlapChars, Integer maxChunksTotal) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("chunkSemantic", String.class, Integer.class, Integer.class, Integer.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(null, text, chunkSizeChars, overlapChars, maxChunksTotal);
    }

    private static void invokeMaybeUpdateMemory(ModerationChunkReviewService service, Long chunkSetId, Map<String, Object> labels, Double score, Verdict verdict) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("maybeUpdateMemory", Long.class, Map.class, Double.class, Verdict.class);
        m.setAccessible(true);
        m.invoke(service, chunkSetId, labels, score, verdict);
    }

    private static Object invokeResolveChunkSizingDecision(ModerationChunkReviewService service, ModerationChunkReviewConfigDTO cfg) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("resolveChunkSizingDecision", ModerationChunkReviewConfigDTO.class);
        m.setAccessible(true);
        return m.invoke(service, cfg);
    }

    private static int effectiveChunkSizeChars(Object decision) throws Exception {
        Method m = decision.getClass().getDeclaredMethod("effectiveChunkSizeChars");
        m.setAccessible(true);
        return (Integer) m.invoke(decision);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> budgetLog(Object decision) throws Exception {
        Method m = decision.getClass().getDeclaredMethod("budgetConvergenceLog");
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(decision);
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

    private static ModerationChunkSetEntity chunkSet(Long id, ContentType contentType, ChunkSetStatus status, Integer total, Long queueId) {
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(id);
        set.setQueueId(queueId);
        set.setContentType(contentType);
        set.setStatus(status);
        set.setTotalChunks(total);
        set.setCompletedChunks(0);
        set.setFailedChunks(0);
        set.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        set.setUpdatedAt(LocalDateTime.now());
        set.setVersion(0);
        return set;
    }

    private static ModerationChunkEntity chunk(Long id, ChunkStatus status, Integer attempts) {
        ModerationChunkEntity c = new ModerationChunkEntity();
        c.setId(id);
        c.setStatus(status);
        c.setAttempts(attempts);
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    private static ModerationChunkReviewConfigDTO cfg() {
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setEnabled(true);
        cfg.setChunkMode("FIXED");
        cfg.setChunkThresholdChars(1000);
        cfg.setChunkSizeChars(500);
        cfg.setOverlapChars(50);
        cfg.setMaxChunksTotal(20);
        cfg.setChunksPerRun(3);
        cfg.setMaxAttempts(3);
        return cfg;
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
            when(configService.getConfig()).thenReturn(cfg());
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
