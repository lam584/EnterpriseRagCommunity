package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkStatus;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceSmallGapRemainingIndependentTest {

    @Test
    void updateImageStageMemory_shouldKeepRiskTagsAbsentWhenIncomingTagsAllBlank() {
        Fixture fx = new Fixture();
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(4101L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setMemoryJson(new LinkedHashMap<>());
        when(fx.chunkSetRepository.findById(4101L)).thenReturn(Optional.of(set));

        fx.service.updateImageStageMemory(4101L, null, Arrays.asList(null, " ", "\t"), "   ");

        verify(fx.chunkSetRepository).saveAndFlush(set);
        Map<String, Object> mem = set.getMemoryJson();
        assertTrue(mem.containsKey("imageRiskTags"));
        assertFalse(mem.containsKey("riskTags"));
        assertFalse(mem.containsKey("imageDescription"));
        assertNotNull(mem.get("updatedAt"));
    }

    @Test
    void getProgress_shouldSkipNullChunkItemsInsideTakeWindow() {
        Fixture fx = new Fixture();
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(4201L);
        set.setQueueId(5201L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setTotalChunks(3);
        set.setUpdatedAt(LocalDateTime.now());
        when(fx.chunkSetRepository.findByQueueId(5201L)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(4201L, List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))).thenReturn(1L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(4201L, List.of(ChunkStatus.FAILED))).thenReturn(0L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(4201L, List.of(ChunkStatus.RUNNING))).thenReturn(1L);

        LocalDateTime now = LocalDateTime.now();
        ModerationChunkEntity c0 = new ModerationChunkEntity();
        c0.setId(1L);
        c0.setSourceType(ChunkSourceType.POST_TEXT);
        c0.setStatus(ChunkStatus.SUCCESS);
        c0.setConfidence(BigDecimal.valueOf(0.2));
        c0.setCreatedAt(now.minusSeconds(3));
        c0.setUpdatedAt(now.minusSeconds(1));
        ModerationChunkEntity c2 = new ModerationChunkEntity();
        c2.setId(2L);
        c2.setStatus(ChunkStatus.RUNNING);
        c2.setCreatedAt(now.minusSeconds(1));
        c2.setUpdatedAt(now);
        when(fx.chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(4201L))
                .thenReturn(Arrays.asList(c0, null, c2));

        AdminModerationChunkProgressDTO out = fx.service.getProgress(5201L, true, 3);

        assertEquals(2, out.getChunks().size());
        assertEquals(1L, out.getChunks().get(0).getId());
        assertEquals(2L, out.getChunks().get(1).getId());
    }

    @Test
    void getProgress_shouldRenderNullChunkStatusAsNull() {
        Fixture fx = new Fixture();
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(4202L);
        set.setQueueId(5202L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setTotalChunks(1);
        set.setUpdatedAt(LocalDateTime.now());
        when(fx.chunkSetRepository.findByQueueId(5202L)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(4202L, List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))).thenReturn(0L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(4202L, List.of(ChunkStatus.FAILED))).thenReturn(0L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(4202L, List.of(ChunkStatus.RUNNING))).thenReturn(1L);

        ModerationChunkEntity c = new ModerationChunkEntity();
        c.setId(9L);
        c.setSourceType(ChunkSourceType.POST_TEXT);
        c.setStatus(null);
        c.setCreatedAt(LocalDateTime.now().minusSeconds(2));
        c.setUpdatedAt(LocalDateTime.now());
        when(fx.chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(4202L)).thenReturn(List.of(c));

        AdminModerationChunkProgressDTO out = fx.service.getProgress(5202L, true, 1);
        assertEquals(1, out.getChunks().size());
        assertNull(out.getChunks().get(0).getStatus());
    }

    @Test
    void resolveChunkSizingDecision_shouldReshardUnderTightBudget() throws Exception {
        Fixture fx = new Fixture();
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setChunkSizeChars(4000);

        ModerationLlmConfigEntity llm = new ModerationLlmConfigEntity();
        llm.setVisionPromptCode("vision-tight-budget");
        when(fx.llmConfigRepository.findAll()).thenReturn(List.of(llm));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity prompt =
                new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        prompt.setVisionImageTokenBudget(1);
        prompt.setVisionMaxImagesPerRequest(50);
        prompt.setVisionHighResolutionImages(true);
        prompt.setVisionMaxPixels(1);
        when(fx.promptsRepository.findByPromptCode("vision-tight-budget")).thenReturn(Optional.of(prompt));

        Object decision = invokeResolveChunkSizingDecision(fx.service, cfg);
        Map<String, Object> log = budgetLog(decision);

        assertTrue((Boolean) log.get("triggeredResharding"));
        assertEquals(512, effectiveChunkSizeChars(decision));
    }

    @Test
    void resolveChunkSizingDecision_shouldUsePromptDefaultsWhenPromptFieldsAreNull() throws Exception {
        Fixture fx = new Fixture();
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setChunkSizeChars(4000);

        ModerationLlmConfigEntity llm = new ModerationLlmConfigEntity();
        llm.setVisionPromptCode("vision-null-fields");
        when(fx.llmConfigRepository.findAll()).thenReturn(List.of(llm));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity prompt =
                new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        when(fx.promptsRepository.findByPromptCode("vision-null-fields")).thenReturn(Optional.of(prompt));

        Object decision = invokeResolveChunkSizingDecision(fx.service, cfg);
        Map<String, Object> log = budgetLog(decision);

        assertEquals(50_000, ((Number) log.get("imageTokenBudget")).intValue());
        assertEquals(10, ((Number) log.get("maxImagesPerRequest")).intValue());
        assertEquals(2_621_440, ((Number) log.get("maxPixels")).intValue());
    }

    @Test
    void evidenceFingerprint_shouldUseRawPathWhenBracedJsonIsInvalid() throws Exception {
        assertEquals("raw|{not-json}", evidenceFingerprint("{not-json}"));
        assertEquals("raw|a b", evidenceFingerprint("A  [[IMAGE_12]]  b"));
    }

    @Test
    void evidenceFingerprint_shouldHandleMismatchedBracesAndContextAfterOnly() throws Exception {
        assertEquals("raw|{abc", evidenceFingerprint("{abc"));
        assertEquals("ctx||tail", evidenceFingerprint("{\"before_context\":\" \",\"after_context\":\"TAIL\"}"));
    }

    @Test
    void updateImageStageMemory_shouldIgnoreNullExistingRiskAndReturnWhenTxResultNull() {
        Fixture fx = new Fixture();
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(4102L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setMemoryJson(new LinkedHashMap<>(Map.of("riskTags", Arrays.asList(null, " old "))));
        when(fx.chunkSetRepository.findById(4102L)).thenReturn(Optional.of(set));

        fx.service.updateImageStageMemory(4102L, null, Arrays.asList(null, " "), null);
        verify(fx.chunkSetRepository).saveAndFlush(set);
        assertEquals(List.of("old"), set.getMemoryJson().get("riskTags"));

        doReturn(null).when(fx.txTemplate).execute(any());
        fx.service.updateImageStageMemory(4102L, 0.9, List.of("new"), "ok");
        verify(fx.chunkSetRepository, times(1)).saveAndFlush(any());
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

    private static String evidenceFingerprint(String raw) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("evidenceFingerprint", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, raw);
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
