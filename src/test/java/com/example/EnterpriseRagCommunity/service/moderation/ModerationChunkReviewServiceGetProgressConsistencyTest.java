package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.*;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ModerationChunkReviewServiceGetProgressConsistencyTest {

    @Autowired
    ModerationChunkReviewService chunkReviewService;

    @Autowired
    ModerationChunkSetRepository chunkSetRepository;

    @Autowired
    ModerationChunkRepository chunkRepository;

    @Autowired
    ModerationQueueRepository queueRepository;

    @Test
    void getProgress_shouldDeriveCountersFromChunks() {
        LocalDateTime now = LocalDateTime.now();

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(1001L);
        q.setStatus(QueueStatus.REVIEWING);
        q.setCurrentStage(QueueStage.LLM);
        q.setPriority(0);
        q.setVersion(0);
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        q = queueRepository.save(q);
        long queueId = q.getId();

        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setQueueId(queueId);
        set.setCaseType(ModerationCaseType.CONTENT);
        set.setContentType(ContentType.POST);
        set.setContentId(1001L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setTotalChunks(3);
        set.setCompletedChunks(0);
        set.setFailedChunks(0);
        set.setCreatedAt(now);
        set.setUpdatedAt(now);
        set.setVersion(0);
        set = chunkSetRepository.save(set);

        ModerationChunkEntity c0 = mkChunk(set.getId(), "post", 0, ChunkStatus.SUCCESS);
        ModerationChunkEntity c1 = mkChunk(set.getId(), "post", 1, ChunkStatus.SUCCESS);
        ModerationChunkEntity c2 = mkChunk(set.getId(), "post", 2, ChunkStatus.SUCCESS);
        chunkRepository.saveAll(List.of(c0, c1, c2));

        AdminModerationChunkProgressDTO p = chunkReviewService.getProgress(queueId, true, 10);
        assertEquals(ChunkSetStatus.DONE.name(), p.getStatus());
        assertEquals(3, p.getTotalChunks());
        assertEquals(3, p.getCompletedChunks());
        assertEquals(0, p.getFailedChunks());
        assertEquals(0, p.getRunningChunks());
        assertNotNull(p.getChunks());
        assertEquals(3, p.getChunks().size());
        assertTrue(p.getChunks().stream().allMatch(it -> "SUCCESS".equals(it.getStatus())));
    }

    @Test
    void refreshSetCountersDebounced_shouldNotMissFinalRefreshWithinInterval() {
        LocalDateTime now = LocalDateTime.now();

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(1002L);
        q.setStatus(QueueStatus.REVIEWING);
        q.setCurrentStage(QueueStage.LLM);
        q.setPriority(0);
        q.setVersion(0);
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        q = queueRepository.save(q);
        long queueId = q.getId();

        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setQueueId(queueId);
        set.setCaseType(ModerationCaseType.CONTENT);
        set.setContentType(ContentType.POST);
        set.setContentId(1002L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setTotalChunks(1);
        set.setCompletedChunks(0);
        set.setFailedChunks(0);
        set.setCreatedAt(now);
        set.setUpdatedAt(now);
        set.setVersion(0);
        set = chunkSetRepository.save(set);

        ModerationChunkEntity c0 = mkChunk(set.getId(), "post", 0, ChunkStatus.RUNNING);
        c0 = chunkRepository.save(c0);

        chunkReviewService.refreshSetCountersNow(set.getId());

        c0.setStatus(ChunkStatus.SUCCESS);
        c0.setUpdatedAt(LocalDateTime.now());
        chunkRepository.save(c0);
        chunkRepository.flush();

        chunkReviewService.refreshSetCountersDebounced(set.getId(), 0);

        AdminModerationChunkProgressDTO refreshed = chunkReviewService.getProgress(queueId, false, 0);
        assertEquals(ChunkSetStatus.DONE.name(), refreshed.getStatus());
        assertEquals(1, refreshed.getCompletedChunks());
        assertEquals(0, refreshed.getFailedChunks());
    }

    private static ModerationChunkEntity mkChunk(Long chunkSetId, String sourceKey, int idx, ChunkStatus st) {
        LocalDateTime now = LocalDateTime.now();
        ModerationChunkEntity c = new ModerationChunkEntity();
        c.setChunkSetId(chunkSetId);
        c.setSourceType(ChunkSourceType.POST_TEXT);
        c.setSourceKey(sourceKey);
        c.setChunkIndex(idx);
        c.setStartOffset(idx * 100);
        c.setEndOffset(idx * 100 + 99);
        c.setStatus(st);
        c.setAttempts(1);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        c.setVersion(0);
        return c;
    }
}
