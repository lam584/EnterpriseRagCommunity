package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ModerationChunkReviewServiceRefreshSetCountersConcurrencyTest {

    @Autowired
    ModerationChunkReviewService chunkReviewService;

    @Autowired
    ModerationChunkSetRepository chunkSetRepository;

    @Autowired
    ModerationChunkRepository chunkRepository;

    @Autowired
    ModerationQueueRepository queueRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void refreshSetCounters_shouldNotPoisonCallerTransactionUnderConcurrency() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(2001L);
        q.setStatus(QueueStatus.REVIEWING);
        q.setCurrentStage(QueueStage.LLM);
        q.setPriority(0);
        q.setVersion(0);
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        q = queueRepository.save(q);

        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setQueueId(q.getId());
        set.setCaseType(ModerationCaseType.CONTENT);
        set.setContentType(ContentType.POST);
        set.setContentId(2001L);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setTotalChunks(40);
        set.setCompletedChunks(0);
        set.setFailedChunks(0);
        set.setCreatedAt(now);
        set.setUpdatedAt(now);
        set.setVersion(0);
        set = chunkSetRepository.save(set);
        Long setId = set.getId();
        Long queueId = q.getId();

        List<ModerationChunkEntity> chunks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ChunkStatus st = (i % 10 == 0) ? ChunkStatus.FAILED : ChunkStatus.SUCCESS;
            ModerationChunkEntity c = new ModerationChunkEntity();
            c.setChunkSetId(setId);
            c.setSourceType(ChunkSourceType.POST_TEXT);
            c.setSourceKey("post");
            c.setChunkIndex(i);
            c.setStartOffset(i * 10);
            c.setEndOffset(i * 10 + 9);
            c.setStatus(st);
            c.setAttempts(1);
            c.setCreatedAt(now);
            c.setUpdatedAt(now);
            c.setVersion(0);
            chunks.add(c);
        }
        chunkRepository.saveAll(chunks);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int threads = 8;
        int loopsPerThread = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    for (int i = 0; i < loopsPerThread; i++) {
                        tx.execute((status) -> {
                            chunkReviewService.refreshSetCountersNow(setId);
                            return null;
                        });
                    }
                } catch (Throwable e) {
                    errors.add(e);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

        chunkReviewService.refreshSetCountersNow(setId);

        ModerationChunkSetEntity refreshed = chunkSetRepository.findById(setId).orElseThrow();
        long ok = chunkRepository.countByChunkSetIdAndStatusIn(setId, List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED));
        long failed = chunkRepository.countByChunkSetIdAndStatusIn(setId, List.of(ChunkStatus.FAILED));
        assertEquals((int) ok, refreshed.getCompletedChunks());
        assertEquals((int) failed, refreshed.getFailedChunks());

        try {
            chunkRepository.deleteAll(chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(setId));
        } catch (Exception ignore) {
        }
        try {
            chunkSetRepository.deleteById(setId);
        } catch (Exception ignore) {
        }
        try {
            queueRepository.deleteById(queueId);
        } catch (Exception ignore) {
        }
    }
}
