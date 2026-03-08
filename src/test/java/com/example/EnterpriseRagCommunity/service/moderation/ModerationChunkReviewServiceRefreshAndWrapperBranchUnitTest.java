package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceRefreshAndWrapperBranchUnitTest {

    @Test
    void markChunkSuccess_wrapperOverload_shouldDelegateWithRefreshSetTrue() {
        Fixture fx = new Fixture();
        ModerationChunkReviewService svc = spy(fx.service);
        doNothing().when(svc).markChunkSuccess(
                eq(11L),
                eq("m"),
                eq(Verdict.APPROVE),
                eq(0.6),
                eq(Map.of("riskTags", List.of("r1"))),
                eq(3),
                eq(4),
                eq(true)
        );

        svc.markChunkSuccess(11L, "m", Verdict.APPROVE, 0.6, Map.of("riskTags", List.of("r1")), 3, 4);

        verify(svc, times(1)).markChunkSuccess(11L, "m", Verdict.APPROVE, 0.6, Map.of("riskTags", List.of("r1")), 3, 4, true);
    }

    @Test
    void markChunkFailed_wrapperOverload_shouldDelegateWithRefreshSetTrue() {
        Fixture fx = new Fixture();
        ModerationChunkReviewService svc = spy(fx.service);
        doNothing().when(svc).markChunkFailed(22L, "boom", true);

        svc.markChunkFailed(22L, "boom");

        verify(svc, times(1)).markChunkFailed(22L, "boom", true);
    }

    @Test
    void refreshSetCountersDebounced_shouldReturnOnNullChunkSetId() {
        Fixture fx = new Fixture();

        fx.service.refreshSetCountersDebounced(null, 500L);

        verify(fx.chunkRepository, never()).flush();
        verify(fx.chunkSetRepository, never()).findById(anyLong());
    }

    @Test
    void refreshSetCountersDebounced_shouldRefreshImmediatelyWhenIntervalNotPositive() {
        Fixture fx = new Fixture();
        TransactionTemplate tx = installImmediateTx(fx);
        ModerationChunkSetEntity set = chunkSet(31L, ChunkSetStatus.RUNNING, 10, 0, 0);
        when(fx.chunkSetRepository.findById(31L)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(31L), eq(List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))))
                .thenReturn(10L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(31L), eq(List.of(ChunkStatus.FAILED))))
                .thenReturn(0L);
        when(fx.chunkSetRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        fx.service.refreshSetCountersDebounced(31L, -1L);

        verify(fx.chunkRepository, never()).flush();
        verify(tx, times(1)).execute(any());
        assertEquals(10, set.getCompletedChunks());
        assertEquals(0, set.getFailedChunks());
        assertEquals(ChunkSetStatus.DONE, set.getStatus());
        assertNotNull(set.getUpdatedAt());
    }

    @Test
    void refreshSetCountersDebounced_shouldSkipRemainingCountWhenAlreadyTerminalWithinInterval() {
        Fixture fx = new Fixture();
        Long setId = 41L;
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Long, AtomicLong> lastRefresh = (ConcurrentHashMap<Long, AtomicLong>) ReflectionTestUtils.getField(fx.service, "lastSetRefreshMs");
        assertNotNull(lastRefresh);
        lastRefresh.put(setId, new AtomicLong(System.currentTimeMillis()));
        ModerationChunkSetEntity set = chunkSet(setId, ChunkSetStatus.DONE, 2, 2, 0);
        when(fx.chunkSetRepository.findById(setId)).thenReturn(Optional.of(set));

        fx.service.refreshSetCountersDebounced(setId, 1000L);

        verify(fx.chunkRepository, times(1)).flush();
        verify(fx.chunkSetRepository, times(1)).findById(setId);
        verify(fx.chunkRepository, never()).countByChunkSetIdAndStatusIn(eq(setId), eq(List.of(ChunkStatus.PENDING, ChunkStatus.RUNNING)));
    }

    @Test
    void refreshSetCountersDebounced_shouldTriggerFinalRefreshWhenNoPendingOrRunning() {
        Fixture fx = new Fixture();
        Long setId = 42L;
        TransactionTemplate tx = installImmediateTx(fx);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Long, AtomicLong> lastRefresh = (ConcurrentHashMap<Long, AtomicLong>) ReflectionTestUtils.getField(fx.service, "lastSetRefreshMs");
        assertNotNull(lastRefresh);
        lastRefresh.put(setId, new AtomicLong(System.currentTimeMillis()));

        ModerationChunkSetEntity set = chunkSet(setId, ChunkSetStatus.RUNNING, 2, 1, 0);
        when(fx.chunkSetRepository.findById(setId)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(setId), eq(List.of(ChunkStatus.PENDING, ChunkStatus.RUNNING))))
                .thenReturn(0L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(setId), eq(List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))))
                .thenReturn(2L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(setId), eq(List.of(ChunkStatus.FAILED))))
                .thenReturn(0L);
        when(fx.chunkSetRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        fx.service.refreshSetCountersDebounced(setId, 1000L);

        verify(fx.chunkRepository, times(1)).flush();
        verify(tx, times(1)).execute(any());
        assertEquals(2, set.getCompletedChunks());
        assertEquals(ChunkSetStatus.DONE, set.getStatus());
    }

    @Test
    void refreshSetCountersNow_shouldRetryWhenOptimisticLockHappens() {
        Fixture fx = new Fixture();
        TransactionTemplate tx = mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(fx.service, "cachedRequiresNewTx", tx);
        AtomicInteger callCount = new AtomicInteger(0);
        when(tx.execute(any())).thenAnswer(invocation -> {
            if (callCount.getAndIncrement() == 0) {
                throw new OptimisticLockingFailureException("conflict");
            }
            @SuppressWarnings("unchecked")
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        ModerationChunkSetEntity set = chunkSet(51L, ChunkSetStatus.RUNNING, 3, 0, 0);
        when(fx.chunkSetRepository.findById(51L)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(51L), eq(List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED))))
                .thenReturn(2L);
        when(fx.chunkRepository.countByChunkSetIdAndStatusIn(eq(51L), eq(List.of(ChunkStatus.FAILED))))
                .thenReturn(1L);
        when(fx.chunkSetRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        fx.service.refreshSetCountersNow(51L);

        verify(tx, times(2)).execute(any());
        assertEquals(2, set.getCompletedChunks());
        assertEquals(1, set.getFailedChunks());
        assertEquals(ChunkSetStatus.DONE, set.getStatus());
    }

    private static TransactionTemplate installImmediateTx(Fixture fx) {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        ReflectionTestUtils.setField(fx.service, "cachedRequiresNewTx", tx);
        return tx;
    }

    private static ModerationChunkSetEntity chunkSet(Long id, ChunkSetStatus status, int total, int done, int failed) {
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(id);
        set.setStatus(status);
        set.setTotalChunks(total);
        set.setCompletedChunks(done);
        set.setFailedChunks(failed);
        return set;
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
            ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
            cfg.setEnabled(true);
            cfg.setEnableGlobalMemory(false);
            cfg.setMaxAttempts(3);
            when(configService.getConfig()).thenReturn(cfg);
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
