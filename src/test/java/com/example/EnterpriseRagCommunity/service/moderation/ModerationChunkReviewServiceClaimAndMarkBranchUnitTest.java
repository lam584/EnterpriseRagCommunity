package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
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
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceClaimAndMarkBranchUnitTest {

    @Test
    void cancelByQueueId_shouldReturnOnNullQueueId() {
        Fixture fx = new Fixture(cfg(3, false));
        fx.service.cancelByQueueId(null);
        verify(fx.chunkSetRepository, never()).findByQueueId(any());
    }

    @Test
    void cancelByQueueId_shouldReturnWhenChunkSetMissing() {
        Fixture fx = new Fixture(cfg(3, false));
        when(fx.chunkSetRepository.findByQueueId(9L)).thenReturn(Optional.empty());
        fx.service.cancelByQueueId(9L);
        verify(fx.chunkSetRepository, never()).save(any());
        verify(fx.chunkRepository, never()).saveAll(any());
    }

    @Test
    void cancelByQueueId_shouldReturnWhenChunkSetAlreadyTerminal() {
        Fixture fx = new Fixture(cfg(3, false));
        ModerationChunkSetEntity set = chunkSet(33L, ChunkSetStatus.DONE);
        when(fx.chunkSetRepository.findByQueueId(8L)).thenReturn(Optional.of(set));
        fx.service.cancelByQueueId(8L);
        verify(fx.chunkSetRepository, never()).save(any());
        verify(fx.chunkRepository, never()).findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(any());
    }

    @Test
    void cancelByQueueId_shouldCancelSetAndChunksAndPersistWhenChunksExist() {
        Fixture fx = new Fixture(cfg(3, false));
        ModerationChunkSetEntity set = chunkSet(61L, ChunkSetStatus.RUNNING);
        when(fx.chunkSetRepository.findByQueueId(6L)).thenReturn(Optional.of(set));

        ModerationChunkEntity success = chunk(1L, 61L, ChunkStatus.SUCCESS, 1);
        ModerationChunkEntity failed = chunk(2L, 61L, ChunkStatus.FAILED, 1);
        ModerationChunkEntity pending = chunk(3L, 61L, ChunkStatus.PENDING, 0);
        when(fx.chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(61L))
                .thenReturn(Arrays.asList(null, success, failed, pending));

        fx.service.cancelByQueueId(6L);

        assertEquals(ChunkSetStatus.CANCELLED, set.getStatus());
        assertNotNull(set.getCancelledAt());
        assertNotNull(set.getUpdatedAt());
        assertEquals(ChunkStatus.SUCCESS, success.getStatus());
        assertEquals(ChunkStatus.CANCELLED, failed.getStatus());
        assertEquals(ChunkStatus.CANCELLED, pending.getStatus());
        verify(fx.chunkSetRepository).save(set);
        verify(fx.chunkRepository).saveAll(any());
    }

    @Test
    void cancelByQueueId_shouldSkipSaveAllWhenChunksEmpty() {
        Fixture fx = new Fixture(cfg(3, false));
        ModerationChunkSetEntity set = chunkSet(62L, ChunkSetStatus.RUNNING);
        when(fx.chunkSetRepository.findByQueueId(7L)).thenReturn(Optional.of(set));
        when(fx.chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(62L)).thenReturn(List.of());
        fx.service.cancelByQueueId(7L);
        verify(fx.chunkRepository, never()).saveAll(any());
    }

    @Test
    void claimNextChunk_shouldReturnEmptyOnNullChunkSetId() {
        Fixture fx = new Fixture(cfg(3, false));
        assertTrue(fx.service.claimNextChunk(null).isEmpty());
    }

    @Test
    void claimNextChunk_shouldReturnEmptyWhenRepositoryReturnsEmpty() {
        Fixture fx = new Fixture(cfg(3, false));
        when(fx.chunkRepository.findNextEligibleForUpdate(anyLong(), any(), any(), anyInt(), any())).thenReturn(List.of());
        assertTrue(fx.service.claimNextChunk(4L).isEmpty());
    }

    @Test
    void claimNextChunk_shouldReturnEmptyWhenFirstRecordIsNull() {
        Fixture fx = new Fixture(cfg(3, false));
        when(fx.chunkRepository.findNextEligibleForUpdate(anyLong(), any(), any(), anyInt(), any()))
                .thenReturn(Arrays.asList((ModerationChunkEntity) null));
        assertTrue(fx.service.claimNextChunk(4L).isEmpty());
    }

    @Test
    void claimNextChunk_shouldReturnEmptyWhenAttemptsExceedLimit() {
        Fixture fx = new Fixture(cfg(2, false));
        ModerationChunkEntity c = chunk(11L, 21L, ChunkStatus.PENDING, 2);
        when(fx.chunkRepository.findNextEligibleForUpdate(anyLong(), any(), any(), anyInt(), any())).thenReturn(List.of(c));
        assertTrue(fx.service.claimNextChunk(21L).isEmpty());
    }

    @Test
    void claimNextChunk_shouldClaimChunkAndIncrementAttempts() {
        Fixture fx = new Fixture(cfg(null, false));
        ModerationChunkEntity c = chunk(12L, 22L, ChunkStatus.FAILED, null);
        c.setFileAssetId(701L);
        c.setFileName("e.txt");
        when(fx.chunkRepository.findNextEligibleForUpdate(anyLong(), any(), any(), anyInt(), any())).thenReturn(List.of(c));

        Optional<ModerationChunkReviewService.ChunkToProcess> out = fx.service.claimNextChunk(22L);

        assertTrue(out.isPresent());
        assertEquals(12L, out.get().chunkId());
        assertEquals(ChunkStatus.RUNNING, c.getStatus());
        assertEquals(1, c.getAttempts());
        assertNull(c.getLastError());
        assertNotNull(c.getUpdatedAt());
        verify(fx.chunkRepository).save(c);
    }

    @Test
    void claimChunkById_shouldCoverGuardBranches() {
        Fixture fx = new Fixture(cfg(2, false));
        assertTrue(fx.service.claimChunkById(null).isEmpty());

        when(fx.chunkRepository.findByIdForUpdate(91L)).thenReturn(List.of());
        assertTrue(fx.service.claimChunkById(91L).isEmpty());

        when(fx.chunkRepository.findByIdForUpdate(92L)).thenReturn(Arrays.asList((ModerationChunkEntity) null));
        assertTrue(fx.service.claimChunkById(92L).isEmpty());

        ModerationChunkEntity running = chunk(93L, 41L, ChunkStatus.RUNNING, 1);
        when(fx.chunkRepository.findByIdForUpdate(93L)).thenReturn(List.of(running));
        assertTrue(fx.service.claimChunkById(93L).isEmpty());

        ModerationChunkEntity maxed = chunk(94L, 41L, ChunkStatus.FAILED, 2);
        when(fx.chunkRepository.findByIdForUpdate(94L)).thenReturn(List.of(maxed));
        assertTrue(fx.service.claimChunkById(94L).isEmpty());
    }

    @Test
    void claimChunkById_shouldClaimWhenPendingAndAttemptAvailable() {
        Fixture fx = new Fixture(cfg(3, false));
        ModerationChunkEntity c = chunk(95L, 41L, ChunkStatus.PENDING, 1);
        when(fx.chunkRepository.findByIdForUpdate(95L)).thenReturn(List.of(c));

        Optional<ModerationChunkReviewService.ChunkToProcess> out = fx.service.claimChunkById(95L);

        assertTrue(out.isPresent());
        assertEquals(95L, out.get().chunkId());
        assertEquals(ChunkStatus.RUNNING, c.getStatus());
        assertEquals(2, c.getAttempts());
        assertNotNull(c.getUpdatedAt());
        verify(fx.chunkRepository).save(c);
    }

    @Test
    void listEligibleChunks_shouldReturnEmptyForNullAndEmptyInputs() {
        Fixture fx = new Fixture(cfg(2, false));
        assertTrue(fx.service.listEligibleChunks(null).isEmpty());

        when(fx.chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(1L)).thenReturn(List.of());
        assertTrue(fx.service.listEligibleChunks(1L).isEmpty());
    }

    @Test
    void listEligibleChunks_shouldFilterByStatusAndAttemptLimit() {
        Fixture fx = new Fixture(cfg(2, false));
        ModerationChunkEntity pending = chunk(101L, 1L, ChunkStatus.PENDING, null);
        ModerationChunkEntity retryable = chunk(102L, 1L, ChunkStatus.FAILED, 1);
        ModerationChunkEntity exhausted = chunk(103L, 1L, ChunkStatus.FAILED, 2);
        ModerationChunkEntity running = chunk(104L, 1L, ChunkStatus.RUNNING, 0);
        when(fx.chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(1L))
                .thenReturn(Arrays.asList(null, pending, retryable, exhausted, running));

        List<ModerationChunkReviewService.ChunkCandidate> out = fx.service.listEligibleChunks(1L);

        assertEquals(2, out.size());
        assertEquals(101L, out.get(0).chunkId());
        assertEquals(0, out.get(0).attempts());
        assertEquals(102L, out.get(1).chunkId());
        assertEquals(1, out.get(1).attempts());
    }

    @Test
    void markChunkSuccess_shouldReturnOnNullAndMissingChunk() {
        Fixture fx = new Fixture(cfg(3, false));
        fx.service.markChunkSuccess(null, "m", Verdict.APPROVE, 0.8, Map.of(), 1, 2, false);
        verify(fx.chunkRepository, never()).findById(any());

        when(fx.chunkRepository.findById(88L)).thenReturn(Optional.empty());
        fx.service.markChunkSuccess(88L, "m", Verdict.APPROVE, 0.8, Map.of(), 1, 2, false);
        verify(fx.chunkRepository, never()).save(any());
    }

    @Test
    void markChunkSuccess_shouldSetFieldsAndClampScore() {
        Fixture fx = new Fixture(cfg(3, false));
        ModerationChunkEntity c = chunk(120L, 88L, ChunkStatus.RUNNING, 1);
        when(fx.chunkRepository.findById(120L)).thenReturn(Optional.of(c));

        fx.service.markChunkSuccess(120L, "gpt-x", Verdict.REJECT, 1.9, Map.of("riskTags", List.of("violence")), 55, 44, false);

        assertEquals(ChunkStatus.SUCCESS, c.getStatus());
        assertEquals("gpt-x", c.getModel());
        assertEquals(Verdict.REJECT, c.getVerdict());
        assertEquals(BigDecimal.valueOf(1.0), c.getConfidence());
        assertEquals(55, c.getTokensIn());
        assertEquals(44, c.getTokensOut());
        assertNotNull(c.getDecidedAt());
        assertNotNull(c.getUpdatedAt());
        verify(fx.chunkRepository).save(c);
    }

    @Test
    void markChunkSuccess_shouldAllowNullScoreAndRefreshSet() {
        Fixture fx = new Fixture(cfg(3, false));
        ModerationChunkEntity c = chunk(121L, 89L, ChunkStatus.RUNNING, 1);
        c.setConfidence(BigDecimal.valueOf(0.5));
        when(fx.chunkRepository.findById(121L)).thenReturn(Optional.of(c));

        fx.service.markChunkSuccess(121L, "gpt-y", Verdict.APPROVE, null, Map.of(), 10, 11, true);

        assertNull(c.getConfidence());
        assertEquals(ChunkStatus.SUCCESS, c.getStatus());
        verify(fx.chunkRepository).save(c);
    }

    @Test
    void markChunkFailed_shouldReturnOnNullAndMissingChunk() {
        Fixture fx = new Fixture(cfg(3, false));
        fx.service.markChunkFailed(null, "x", false);
        verify(fx.chunkRepository, never()).findById(any());

        when(fx.chunkRepository.findById(130L)).thenReturn(Optional.empty());
        fx.service.markChunkFailed(130L, "x", false);
        verify(fx.chunkRepository, never()).save(any());
    }

    @Test
    void markChunkFailed_shouldTrimAndTruncateError() {
        Fixture fx = new Fixture(cfg(3, false));
        ModerationChunkEntity c = chunk(131L, 50L, ChunkStatus.RUNNING, 1);
        when(fx.chunkRepository.findById(131L)).thenReturn(Optional.of(c));
        String longErr = " " + "x".repeat(1005) + " ";

        fx.service.markChunkFailed(131L, longErr, false);

        assertEquals(ChunkStatus.FAILED, c.getStatus());
        assertNotNull(c.getUpdatedAt());
        assertNotNull(c.getLastError());
        assertEquals(1000, c.getLastError().length());
        verify(fx.chunkRepository).save(c);
    }

    @Test
    void markChunkFailed_shouldKeepLastErrorWhenInputErrorIsNull() {
        Fixture fx = new Fixture(cfg(3, false));
        ModerationChunkEntity c = chunk(132L, 51L, ChunkStatus.RUNNING, 1);
        c.setLastError("existing");
        when(fx.chunkRepository.findById(132L)).thenReturn(Optional.of(c));

        fx.service.markChunkFailed(132L, null, true);

        assertEquals(ChunkStatus.FAILED, c.getStatus());
        assertEquals("existing", c.getLastError());
        verify(fx.chunkRepository).save(c);
    }

    private static ModerationChunkReviewConfigDTO cfg(Integer maxAttempts, Boolean enableGlobalMemory) {
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setEnabled(true);
        cfg.setMaxAttempts(maxAttempts);
        cfg.setEnableGlobalMemory(enableGlobalMemory);
        return cfg;
    }

    private static ModerationChunkSetEntity chunkSet(Long id, ChunkSetStatus status) {
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(id);
        set.setStatus(status);
        return set;
    }

    private static ModerationChunkEntity chunk(Long id, Long chunkSetId, ChunkStatus status, Integer attempts) {
        ModerationChunkEntity c = new ModerationChunkEntity();
        c.setId(id);
        c.setChunkSetId(chunkSetId);
        c.setSourceType(ChunkSourceType.POST_TEXT);
        c.setSourceKey("post");
        c.setChunkIndex(0);
        c.setStartOffset(0);
        c.setEndOffset(99);
        c.setStatus(status);
        c.setAttempts(attempts);
        return c;
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

        Fixture(ModerationChunkReviewConfigDTO cfg) {
            when(configService.getConfig()).thenReturn(cfg);
            when(chunkSetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chunkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
