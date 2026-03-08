package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationChunkReviewServiceSetAndLookupBranchUnitTest {

    @Test
    void findSetByQueueId_shouldHandleNullAndLookup() {
        Fixture fx = new Fixture();
        assertTrue(fx.service.findSetByQueueId(null).isEmpty());
        verify(fx.chunkSetRepository, never()).findByQueueId(any());

        ModerationChunkSetEntity set = chunkSet(11L, 21L, ChunkSetStatus.RUNNING, ContentType.POST);
        when(fx.chunkSetRepository.findByQueueId(21L)).thenReturn(Optional.of(set));
        Optional<ModerationChunkSetEntity> out = fx.service.findSetByQueueId(21L);
        assertTrue(out.isPresent());
        assertSame(set, out.get());
    }

    @Test
    void chunksPerRun_shouldUseDefaultAndClampBounds() {
        Fixture fx = new Fixture();

        when(fx.configService.getConfig()).thenReturn(null);
        assertEquals(3, fx.service.chunksPerRun());

        ModerationChunkReviewConfigDTO low = cfg();
        low.setChunksPerRun(0);
        when(fx.configService.getConfig()).thenReturn(low);
        assertEquals(1, fx.service.chunksPerRun());

        ModerationChunkReviewConfigDTO high = cfg();
        high.setChunksPerRun(80);
        when(fx.configService.getConfig()).thenReturn(high);
        assertEquals(50, fx.service.chunksPerRun());
    }

    @Test
    void getConfig_shouldReturnValueFromConfigService() {
        Fixture fx = new Fixture();
        ModerationChunkReviewConfigDTO cfg = cfg();
        cfg.setChunksPerRun(12);
        when(fx.configService.getConfig()).thenReturn(cfg);
        assertSame(cfg, fx.service.getConfig());
    }

    @Test
    void getMemory_shouldHandleNullMissingAndExisting() {
        Fixture fx = new Fixture();
        assertEquals(Map.of(), fx.service.getMemory(null));

        when(fx.chunkSetRepository.findById(1L)).thenReturn(Optional.empty());
        assertEquals(Map.of(), fx.service.getMemory(1L));

        ModerationChunkSetEntity noMem = chunkSet(2L, 102L, ChunkSetStatus.RUNNING, ContentType.POST);
        noMem.setMemoryJson(null);
        when(fx.chunkSetRepository.findById(2L)).thenReturn(Optional.of(noMem));
        assertEquals(Map.of(), fx.service.getMemory(2L));

        Map<String, Object> mem = new LinkedHashMap<>(Map.of("riskTags", List.of("r1")));
        ModerationChunkSetEntity hasMem = chunkSet(3L, 103L, ChunkSetStatus.RUNNING, ContentType.POST);
        hasMem.setMemoryJson(mem);
        when(fx.chunkSetRepository.findById(3L)).thenReturn(Optional.of(hasMem));
        assertSame(mem, fx.service.getMemory(3L));
    }

    @Test
    void loadProgressSummaries_shouldHandleEmptyAndMapFields() {
        Fixture fx = new Fixture();
        assertEquals(Map.of(), fx.service.loadProgressSummaries(null));
        assertEquals(Map.of(), fx.service.loadProgressSummaries(List.of()));
        verify(fx.chunkSetRepository, never()).findAllByQueueIds(any());

        when(fx.chunkSetRepository.findAllByQueueIds(any())).thenReturn(Arrays.asList(
                null,
                chunkSet(5L, null, ChunkSetStatus.RUNNING, ContentType.POST),
                chunkSetWithCounters(6L, 206L, ChunkSetStatus.RUNNING, -1, 2, 1)
        ));
        Map<Long, ModerationChunkReviewService.ProgressSummary> out = fx.service.loadProgressSummaries(List.of(206L));
        assertEquals(1, out.size());
        ModerationChunkReviewService.ProgressSummary ps = out.get(206L);
        assertNotNull(ps);
        assertEquals(206L, ps.queueId);
        assertEquals("RUNNING", ps.status);
        assertEquals(0, ps.total);
        assertEquals(2, ps.completed);
        assertEquals(1, ps.failed);
    }

    @Test
    void countPendingOrFailed_shouldHandleNullAndClampAttempts() {
        Fixture fx = new Fixture();
        assertEquals(0L, fx.service.countPendingOrFailed(null));
        verify(fx.chunkRepository, never()).countRetriableByChunkSetId(any(), any(), any(), any(Integer.class));

        ModerationChunkReviewConfigDTO cfg = cfg();
        cfg.setMaxAttempts(0);
        when(fx.configService.getConfig()).thenReturn(cfg);
        when(fx.chunkRepository.countRetriableByChunkSetId(eq(88L), any(), any(), eq(1))).thenReturn(7L);
        assertEquals(7L, fx.service.countPendingOrFailed(88L));

        when(fx.configService.getConfig()).thenReturn(null);
        when(fx.chunkRepository.countRetriableByChunkSetId(eq(99L), any(), any(), eq(3))).thenReturn(9L);
        assertEquals(9L, fx.service.countPendingOrFailed(99L));
    }

    @Test
    void loadChunkText_shouldCoverBranches() {
        Fixture fx = new Fixture();
        assertTrue(fx.service.loadChunkText(null, ChunkSourceType.POST_TEXT, null, 0, 4).isEmpty());

        when(fx.chunkSetRepository.findByQueueId(10L)).thenReturn(Optional.empty());
        assertTrue(fx.service.loadChunkText(10L, ChunkSourceType.POST_TEXT, null, 0, 4).isEmpty());

        ModerationChunkSetEntity nonPostSet = chunkSet(11L, 11L, ChunkSetStatus.RUNNING, ContentType.COMMENT);
        when(fx.chunkSetRepository.findByQueueId(11L)).thenReturn(Optional.of(nonPostSet));
        assertTrue(fx.service.loadChunkText(11L, ChunkSourceType.POST_TEXT, null, 0, 4).isEmpty());

        ModerationChunkSetEntity postSet = chunkSet(12L, 12L, ChunkSetStatus.RUNNING, ContentType.POST);
        postSet.setContentId(301L);
        when(fx.chunkSetRepository.findByQueueId(12L)).thenReturn(Optional.of(postSet));
        when(fx.postsRepository.findById(301L)).thenReturn(Optional.empty());
        assertTrue(fx.service.loadChunkText(12L, ChunkSourceType.POST_TEXT, null, 0, 4).isEmpty());

        PostsEntity post = new PostsEntity();
        post.setId(301L);
        post.setContent("0123456789");
        when(fx.postsRepository.findById(301L)).thenReturn(Optional.of(post));
        Optional<String> postText = fx.service.loadChunkText(12L, ChunkSourceType.POST_TEXT, null, -5, 50);
        assertTrue(postText.isPresent());
        assertEquals("0123456789", postText.get());

        assertTrue(fx.service.loadChunkText(12L, ChunkSourceType.FILE_TEXT, null, 0, 4).isEmpty());

        when(fx.fileAssetExtractionsRepository.findById(701L)).thenReturn(Optional.empty());
        assertTrue(fx.service.loadChunkText(12L, ChunkSourceType.FILE_TEXT, 701L, 0, 4).isEmpty());

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(702L);
        ex.setExtractedText("abcdefghij");
        when(fx.fileAssetExtractionsRepository.findById(702L)).thenReturn(Optional.of(ex));
        Optional<String> fileText = fx.service.loadChunkText(12L, ChunkSourceType.FILE_TEXT, 702L, 2, 6);
        assertTrue(fileText.isPresent());
        assertEquals("cdef", fileText.get());
    }

    @Test
    void createChunkSet_shouldPopulateFieldsAndPersist() throws Exception {
        Fixture fx = new Fixture();
        ModerationQueueEntity q = queue(501L, ContentType.POST, 999L);
        when(fx.chunkSetRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ModerationChunkSetEntity set = invocation.getArgument(0);
            set.setId(1001L);
            return set;
        });

        ModerationChunkSetEntity out = invokeCreateChunkSet(fx.service, q);

        assertEquals(1001L, out.getId());
        assertEquals(501L, out.getQueueId());
        assertEquals(ModerationCaseType.CONTENT, out.getCaseType());
        assertEquals(ContentType.POST, out.getContentType());
        assertEquals(999L, out.getContentId());
        assertEquals(ChunkSetStatus.PENDING, out.getStatus());
        assertEquals(0, out.getTotalChunks());
        assertEquals(0, out.getCompletedChunks());
        assertEquals(0, out.getFailedChunks());
        assertNotNull(out.getCreatedAt());
        assertNotNull(out.getUpdatedAt());
        assertEquals(0, out.getVersion());
    }

    @Test
    void ensureChunkSetForQueue_shouldCoverMainAndLambdaBranches() {
        Fixture fx = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> fx.service.ensureChunkSetForQueue(null));
        assertThrows(IllegalArgumentException.class, () -> fx.service.ensureChunkSetForQueue(new ModerationQueueEntity()));

        ModerationQueueEntity q = queue(600L, ContentType.POST, 1000L);
        ModerationChunkSetEntity existing = chunkSet(61L, 600L, ChunkSetStatus.RUNNING, ContentType.POST);
        when(fx.chunkSetRepository.findByQueueId(600L)).thenReturn(Optional.of(existing));
        assertSame(existing, fx.service.ensureChunkSetForQueue(q));
        verify(fx.txTemplate, never()).execute(any());

        ModerationQueueEntity q2 = queue(601L, ContentType.POST, 1001L);
        when(fx.chunkSetRepository.findByQueueId(601L)).thenReturn(Optional.empty());
        when(fx.chunkSetRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ModerationChunkSetEntity set = invocation.getArgument(0);
            if (set == null) return null;
            set.setId(71L);
            return set;
        });
        ModerationChunkSetEntity created = fx.service.ensureChunkSetForQueue(q2);
        assertEquals(71L, created.getId());
        assertEquals(601L, created.getQueueId());

        ModerationQueueEntity q3 = queue(602L, ContentType.POST, 1002L);
        ModerationChunkSetEntity existingFromInnerCatch = chunkSet(72L, 602L, ChunkSetStatus.RUNNING, ContentType.POST);
        when(fx.chunkSetRepository.findByQueueId(602L)).thenReturn(Optional.empty(), Optional.of(existingFromInnerCatch));
        when(fx.chunkSetRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        ModerationChunkSetEntity out3 = fx.service.ensureChunkSetForQueue(q3);
        assertSame(existingFromInnerCatch, out3);

        Fixture fxOuter = new Fixture();
        ModerationQueueEntity q4 = queue(603L, ContentType.POST, 1003L);
        ModerationChunkSetEntity existingFromOuterCatch = chunkSet(73L, 603L, ChunkSetStatus.RUNNING, ContentType.POST);
        when(fxOuter.chunkSetRepository.findByQueueId(603L)).thenReturn(Optional.empty(), Optional.of(existingFromOuterCatch));
        TransactionTemplate throwingTx = mock(TransactionTemplate.class);
        when(throwingTx.execute(any())).thenThrow(new DataIntegrityViolationException("tx-fail"));
        ReflectionTestUtils.setField(fxOuter.service, "cachedRequiresNewTx", throwingTx);
        ModerationChunkSetEntity out4 = fxOuter.service.ensureChunkSetForQueue(q4);
        assertSame(existingFromOuterCatch, out4);

        Fixture fxFail = new Fixture();
        ModerationQueueEntity q5 = queue(604L, ContentType.POST, 1004L);
        when(fxFail.chunkSetRepository.findByQueueId(604L)).thenReturn(Optional.empty(), Optional.empty());
        TransactionTemplate nullTx = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return null;
            }
        };
        ReflectionTestUtils.setField(fxFail.service, "cachedRequiresNewTx", nullTx);
        assertThrows(IllegalStateException.class, () -> fxFail.service.ensureChunkSetForQueue(q5));
    }

    private static ModerationChunkSetEntity invokeCreateChunkSet(ModerationChunkReviewService service, ModerationQueueEntity q) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("createChunkSet", ModerationQueueEntity.class);
        m.setAccessible(true);
        return (ModerationChunkSetEntity) m.invoke(service, q);
    }

    private static ModerationChunkReviewConfigDTO cfg() {
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setEnabled(true);
        cfg.setEnableGlobalMemory(false);
        cfg.setChunksPerRun(3);
        cfg.setMaxAttempts(3);
        return cfg;
    }

    private static ModerationQueueEntity queue(Long id, ContentType contentType, Long contentId) {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(id);
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(contentType);
        q.setContentId(contentId);
        q.setCreatedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());
        return q;
    }

    private static ModerationChunkSetEntity chunkSet(Long id, Long queueId, ChunkSetStatus status, ContentType contentType) {
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setId(id);
        set.setQueueId(queueId);
        set.setCaseType(ModerationCaseType.CONTENT);
        set.setContentType(contentType);
        set.setContentId(100L);
        set.setStatus(status);
        set.setCreatedAt(LocalDateTime.now());
        set.setUpdatedAt(LocalDateTime.now());
        set.setVersion(0);
        return set;
    }

    private static ModerationChunkSetEntity chunkSetWithCounters(Long id, Long queueId, ChunkSetStatus status, Integer total, Integer completed, Integer failed) {
        ModerationChunkSetEntity set = chunkSet(id, queueId, status, ContentType.POST);
        set.setTotalChunks(total);
        set.setCompletedChunks(completed);
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
        final TransactionTemplate txTemplate = mock(TransactionTemplate.class);
        final ModerationChunkReviewService service;

        Fixture() {
            when(configService.getConfig()).thenReturn(cfg());
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
