package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceRemainingBranchUnitTest {

    @Test
    void refreshSetCountersDebounced_shouldCoverCompareAndSetFailurePath() throws InterruptedException {
        Fixture fx = new Fixture();
        AtomicInteger txExecCount = new AtomicInteger(0);
        installImmediateTx(fx, txExecCount);

        Long setId = 901L;
        ModerationChunkSetEntity set = set(setId, ChunkSetStatus.RUNNING, 2, 0, 0);
        when(fx.chunkSetRepository.findById(setId)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(setId), eq(List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))))
                .thenReturn(1L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(setId), eq(List.of(ChunkStatus.FAILED))))
                .thenReturn(0L);
        when(fx.chunkSetRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Long, AtomicLong> lastRefresh = (ConcurrentHashMap<Long, AtomicLong>) ReflectionTestUtils.getField(fx.service, "lastSetRefreshMs");
        assertNotNull(lastRefresh);
        lastRefresh.put(setId, new AtomicLong(0L));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(setId), eq(List.of(ChunkStatus.PENDING, ChunkStatus.RUNNING))))
                .thenReturn(1L);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> error = new AtomicReference<>();
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    assertTrue(start.await(3, TimeUnit.SECONDS));
                    fx.service.refreshSetCountersDebounced(setId, 60_000L);
                } catch (Throwable e) {
                    error.set(e);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertNull(error.get());
        assertEquals(1, txExecCount.get());
    }

    @Test
    void refreshSetCountersDebounced_shouldReturnWhenChunkSetMissingWithinInterval() {
        Fixture fx = new Fixture();
        installImmediateTx(fx, new AtomicInteger(0));
        Long setId = 906L;

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Long, AtomicLong> lastRefresh = (ConcurrentHashMap<Long, AtomicLong>) ReflectionTestUtils.getField(fx.service, "lastSetRefreshMs");
        assertNotNull(lastRefresh);
        lastRefresh.put(setId, new AtomicLong(System.currentTimeMillis()));
        when(fx.chunkSetRepository.findById(setId)).thenReturn(Optional.empty());

        fx.service.refreshSetCountersDebounced(setId, 60_000L);

        verify(fx.chunkSetRepository).findById(setId);
        verify(fx.chunkRepository, never()).countByChunkSetIdAndStatusIn(eq(setId), eq(List.of(ChunkStatus.PENDING, ChunkStatus.RUNNING)));
    }

    @Test
    void refreshSetCountersDebounced_shouldReturnWhenRemainingPositiveWithinInterval() {
        Fixture fx = new Fixture();
        AtomicInteger txExecCount = new AtomicInteger(0);
        installImmediateTx(fx, txExecCount);
        Long setId = 907L;
        ModerationChunkSetEntity set = set(setId, ChunkSetStatus.RUNNING, 3, 1, 0);
        when(fx.chunkSetRepository.findById(setId)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(setId), eq(List.of(ChunkStatus.PENDING, ChunkStatus.RUNNING))))
                .thenReturn(2L);

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Long, AtomicLong> lastRefresh = (ConcurrentHashMap<Long, AtomicLong>) ReflectionTestUtils.getField(fx.service, "lastSetRefreshMs");
        assertNotNull(lastRefresh);
        lastRefresh.put(setId, new AtomicLong(System.currentTimeMillis()));

        fx.service.refreshSetCountersDebounced(setId, 60_000L);

        assertEquals(0, txExecCount.get());
        verify(fx.chunkSetRepository).findById(setId);
        verify(fx.chunkRepository).countByChunkSetIdAndStatusIn(eq(setId), eq(List.of(ChunkStatus.PENDING, ChunkStatus.RUNNING)));
    }

    @Test
    void refreshSetCountersDebounced_shouldSkipFinalRefreshWhenTotalNotPositive() {
        Fixture fx = new Fixture();
        AtomicInteger txExecCount = new AtomicInteger(0);
        installImmediateTx(fx, txExecCount);
        Long setId = 908L;
        ModerationChunkSetEntity set = set(setId, ChunkSetStatus.RUNNING, 0, 0, 0);
        when(fx.chunkSetRepository.findById(setId)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(setId), eq(List.of(ChunkStatus.PENDING, ChunkStatus.RUNNING))))
                .thenReturn(0L);

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Long, AtomicLong> lastRefresh = (ConcurrentHashMap<Long, AtomicLong>) ReflectionTestUtils.getField(fx.service, "lastSetRefreshMs");
        assertNotNull(lastRefresh);
        lastRefresh.put(setId, new AtomicLong(System.currentTimeMillis()));

        fx.service.refreshSetCountersDebounced(setId, 60_000L);

        assertEquals(0, txExecCount.get());
        verify(fx.chunkSetRepository).findById(setId);
        verify(fx.chunkRepository).countByChunkSetIdAndStatusIn(eq(setId), eq(List.of(ChunkStatus.PENDING, ChunkStatus.RUNNING)));
    }

    @Test
    void refreshSetCountersNow_shouldReturnWhenChunkSetMissing() {
        Fixture fx = new Fixture();
        AtomicInteger txExecCount = new AtomicInteger(0);
        installImmediateTx(fx, txExecCount);

        when(fx.chunkSetRepository.findById(902L)).thenReturn(Optional.empty());

        fx.service.refreshSetCountersNow(902L);

        assertEquals(1, txExecCount.get());
        verify(fx.chunkRepository, never()).countByChunkSetIdAndStatusIn(eq(902L), eq(List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED)));
        verify(fx.chunkRepository, never()).countByChunkSetIdAndStatusIn(eq(902L), eq(List.of(ChunkStatus.FAILED)));
    }

    @Test
    void refreshSetCountersNow_shouldKeepCancelledStatusInBothStatusBranches() {
        Fixture fx = new Fixture();
        installImmediateTx(fx, new AtomicInteger(0));

        ModerationChunkSetEntity doneBranch = set(903L, ChunkSetStatus.CANCELLED, 2, 0, 0);
        when(fx.chunkSetRepository.findById(903L)).thenReturn(Optional.of(doneBranch));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(903L), eq(List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))))
                .thenReturn(2L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(903L), eq(List.of(ChunkStatus.FAILED))))
                .thenReturn(0L);
        when(fx.chunkSetRepository.saveAndFlush(doneBranch)).thenReturn(doneBranch);

        ModerationChunkSetEntity runningBranch = set(904L, ChunkSetStatus.CANCELLED, 3, 0, 0);
        when(fx.chunkSetRepository.findById(904L)).thenReturn(Optional.of(runningBranch));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(904L), eq(List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))))
                .thenReturn(1L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(904L), eq(List.of(ChunkStatus.FAILED))))
                .thenReturn(0L);
        when(fx.chunkSetRepository.saveAndFlush(runningBranch)).thenReturn(runningBranch);

        fx.service.refreshSetCountersNow(903L);
        fx.service.refreshSetCountersNow(904L);

        assertEquals(ChunkSetStatus.CANCELLED, doneBranch.getStatus());
        assertEquals(ChunkSetStatus.CANCELLED, runningBranch.getStatus());
    }

    @Test
    void getProgress_shouldCoverElapsedNullCombinations() {
        Fixture fx = new Fixture();
        ModerationChunkSetEntity set = set(905L, ChunkSetStatus.RUNNING, 4, 0, 0);
        when(fx.chunkSetRepository.findByQueueId(55L)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(905L, List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))).thenReturn(1L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(905L, List.of(ChunkStatus.FAILED))).thenReturn(0L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(905L, List.of(ChunkStatus.RUNNING))).thenReturn(1L);

        LocalDateTime now = LocalDateTime.now();
        ModerationChunkEntity createdOnly = chunk(1001L, now.minusSeconds(3), null, null);
        ModerationChunkEntity bothNull = chunk(1002L, null, null, null);
        ModerationChunkEntity endOnly = chunk(1003L, null, null, now.minusSeconds(1));
        ModerationChunkEntity fullElapsed = chunk(1004L, now.minusSeconds(5), now.minusSeconds(2), now.minusSeconds(2));
        when(fx.chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(905L))
                .thenReturn(List.of(createdOnly, bothNull, endOnly, fullElapsed));

        AdminModerationChunkProgressDTO out = fx.service.getProgress(55L, true, 10);

        assertEquals(4, out.getChunks().size());
        assertNull(out.getChunks().get(0).getElapsedMs());
        assertNull(out.getChunks().get(1).getElapsedMs());
        assertNull(out.getChunks().get(2).getElapsedMs());
        Long elapsed = out.getChunks().get(3).getElapsedMs();
        assertNotNull(elapsed);
        assertTrue(elapsed >= 2500L && elapsed <= 3500L);
    }

    private static TransactionTemplate installImmediateTx(Fixture fx, AtomicInteger txExecCount) {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(invocation -> {
            txExecCount.incrementAndGet();
            @SuppressWarnings("unchecked")
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        ReflectionTestUtils.setField(fx.service, "cachedRequiresNewTx", tx);
        return tx;
    }

    private static ModerationChunkSetEntity set(Long id, ChunkSetStatus status, int total, int done, int failed) {
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(id);
        set.setQueueId(id + 1000L);
        set.setStatus(status);
        set.setTotalChunks(total);
        set.setCompletedChunks(done);
        set.setFailedChunks(failed);
        set.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        set.setUpdatedAt(LocalDateTime.now());
        set.setVersion(0);
        return set;
    }

    private static ModerationChunkEntity chunk(Long id, LocalDateTime createdAt, LocalDateTime decidedAt, LocalDateTime updatedAt) {
        ModerationChunkEntity c = new ModerationChunkEntity();
        c.setId(id);
        c.setStatus(ChunkStatus.RUNNING);
        c.setConfidence(BigDecimal.valueOf(0.5));
        c.setCreatedAt(createdAt);
        c.setDecidedAt(decidedAt);
        c.setUpdatedAt(updatedAt);
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
        final ModerationChunkReviewService service;

        Fixture() {
            when(configService.getConfig()).thenReturn(cfg());
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
        }
    }
}
