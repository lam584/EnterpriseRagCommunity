package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "security.access-refresh.enabled=false"
})
class LlmQueueMonitorServiceNonBlockingTest {

    @Autowired
    LlmQueueMonitorService llmQueueMonitorService;

    @Autowired
    LlmCallQueueService llmCallQueueService;

    @Test
    void query_shouldReturnStaleQuickly_whenQueueLockContended() throws Exception {
        llmQueueMonitorService.query(300, 50, 200, 10);

        ReentrantLock lock = (ReentrantLock) ReflectionTestUtils.getField(llmCallQueueService, "lock");
        assertNotNull(lock);

        CountDownLatch locked = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                locked.countDown();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        try {
            assertTrue(locked.await(2, TimeUnit.SECONDS));
            long startedNs = System.nanoTime();
            var out = llmQueueMonitorService.query(300, 50, 200, 10);
            long costMs = (System.nanoTime() - startedNs) / 1_000_000;
            assertNotNull(out);
            assertTrue(Boolean.TRUE.equals(out.getStale()));
            assertTrue(costMs < 500, "costMs=" + costMs);
        } finally {
            try {
                holder.join(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void query_shouldClampLargeLimits_andMarkTruncated() {
        long startedNs = System.nanoTime();
        var out = llmQueueMonitorService.query(300, 999999, 100000, 10);
        long costMs = (System.nanoTime() - startedNs) / 1_000_000;

        assertNotNull(out);
        assertTrue(Boolean.TRUE.equals(out.getTruncated()));
        assertTrue(out.getRunning() == null || out.getRunning().size() <= 500);
        assertTrue(out.getPending() == null || out.getPending().size() <= 2000);
        assertTrue(costMs < 1500, "costMs=" + costMs);
    }
}
