package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkStatus;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceGetProgressBranchUnitTest {

    @Test
    void getProgress_shouldReturnNoneWhenSetMissing() {
        Fixture fx = new Fixture();
        when(fx.chunkSetRepository.findByQueueId(11L)).thenReturn(Optional.empty());

        AdminModerationChunkProgressDTO out = fx.service.getProgress(11L, true, 10);

        assertEquals(11L, out.getQueueId());
        assertEquals("NONE", out.getStatus());
        assertEquals(0, out.getTotalChunks());
        assertEquals(0, out.getCompletedChunks());
        assertEquals(0, out.getFailedChunks());
        assertEquals(0, out.getRunningChunks());
        assertNull(out.getUpdatedAt());
        assertNotNull(out.getChunks());
        assertTrue(out.getChunks().isEmpty());
    }

    @Test
    void getProgress_shouldSkipChunkQueryWhenIncludeChunksFalse() {
        Fixture fx = new Fixture();
        ModerationChunkSetEntity set = set(101L, ChunkSetStatus.RUNNING, 3);
        when(fx.chunkSetRepository.findByQueueId(21L)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(any(), any())).thenReturn(0L);

        AdminModerationChunkProgressDTO out = fx.service.getProgress(21L, false, 5);

        assertEquals("RUNNING", out.getStatus());
        assertNotNull(out.getChunks());
        assertTrue(out.getChunks().isEmpty());
        verify(fx.chunkRepository, never()).findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(any());
    }

    @Test
    void getProgress_shouldResolveStatusCancelledDoneRunning() {
        Fixture fx = new Fixture();

        ModerationChunkSetEntity cancelled = set(201L, ChunkSetStatus.CANCELLED, 2);
        when(fx.chunkSetRepository.findByQueueId(31L)).thenReturn(Optional.of(cancelled));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(201L, List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))).thenReturn(2L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(201L, List.of(ChunkStatus.FAILED))).thenReturn(0L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(201L, List.of(ChunkStatus.RUNNING))).thenReturn(0L);
        assertEquals("CANCELLED", fx.service.getProgress(31L, false, 0).getStatus());

        ModerationChunkSetEntity done = set(202L, ChunkSetStatus.RUNNING, 3);
        when(fx.chunkSetRepository.findByQueueId(32L)).thenReturn(Optional.of(done));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(202L, List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))).thenReturn(2L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(202L, List.of(ChunkStatus.FAILED))).thenReturn(1L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(202L, List.of(ChunkStatus.RUNNING))).thenReturn(0L);
        assertEquals("DONE", fx.service.getProgress(32L, false, 0).getStatus());

        ModerationChunkSetEntity running = set(203L, ChunkSetStatus.RUNNING, 3);
        when(fx.chunkSetRepository.findByQueueId(33L)).thenReturn(Optional.of(running));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(203L, List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))).thenReturn(1L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(203L, List.of(ChunkStatus.FAILED))).thenReturn(0L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(203L, List.of(ChunkStatus.RUNNING))).thenReturn(1L);
        assertEquals("RUNNING", fx.service.getProgress(33L, false, 0).getStatus());
    }

    @Test
    void getProgress_shouldHandleChunkLimitBoundsAndElapsedMs() {
        Fixture fx = new Fixture();
        ModerationChunkSetEntity set = set(301L, ChunkSetStatus.RUNNING, 2);
        when(fx.chunkSetRepository.findByQueueId(41L)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(301L, List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))).thenReturn(0L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(301L, List.of(ChunkStatus.FAILED))).thenReturn(0L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(301L, List.of(ChunkStatus.RUNNING))).thenReturn(1L);

        LocalDateTime start = LocalDateTime.now().minusSeconds(2);
        ModerationChunkEntity c0 = chunk(501L, ChunkStatus.SUCCESS, Verdict.APPROVE, start, start.plusNanos(550_000_000L));
        ModerationChunkEntity c1 = chunk(502L, ChunkStatus.RUNNING, null, null, LocalDateTime.now());
        when(fx.chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(301L)).thenReturn(List.of(c0, c1));

        AdminModerationChunkProgressDTO negative = fx.service.getProgress(41L, true, -9);
        assertNotNull(negative.getChunks());
        assertTrue(negative.getChunks().isEmpty());

        AdminModerationChunkProgressDTO over = fx.service.getProgress(41L, true, 99);
        assertEquals(2, over.getChunks().size());
        Long elapsed0 = over.getChunks().get(0).getElapsedMs();
        assertNotNull(elapsed0);
        assertTrue(elapsed0 >= 500L && elapsed0 <= 1000L);
        assertNull(over.getChunks().get(1).getElapsedMs());
    }

    private static ModerationChunkSetEntity set(Long id, ChunkSetStatus status, Integer total) {
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(id);
        set.setQueueId(id + 1000L);
        set.setStatus(status);
        set.setTotalChunks(total);
        set.setCompletedChunks(0);
        set.setFailedChunks(0);
        set.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        set.setUpdatedAt(LocalDateTime.now());
        set.setVersion(0);
        return set;
    }

    private static ModerationChunkEntity chunk(Long id, ChunkStatus status, Verdict verdict, LocalDateTime createdAt, LocalDateTime decidedAt) {
        ModerationChunkEntity c = new ModerationChunkEntity();
        c.setId(id);
        c.setStatus(status);
        c.setVerdict(verdict);
        c.setConfidence(BigDecimal.valueOf(0.7));
        c.setAttempts(1);
        c.setLastError(null);
        c.setCreatedAt(createdAt);
        c.setDecidedAt(decidedAt);
        c.setUpdatedAt(decidedAt);
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
