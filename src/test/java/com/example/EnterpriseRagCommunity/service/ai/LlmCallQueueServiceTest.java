package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmQueueTaskHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class LlmCallQueueServiceTest {

    private static void waitUntil(BooleanSupplier condition, long timeoutMs, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        fail(message);
    }

    @Test
    void shouldQueueAndRespectMaxConcurrent() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(2);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(1000);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        int tasks = 20;
        AtomicInteger running = new AtomicInteger(0);
        AtomicInteger maxRunning = new AtomicInteger(0);
        CountDownLatch ready = new CountDownLatch(tasks);
        CountDownLatch start = new CountDownLatch(1);

        var pool = Executors.newFixedThreadPool(tasks);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < tasks; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(3, TimeUnit.SECONDS);
                return queue.call(
                        LlmQueueTaskType.CHAT,
                        "p1",
                        "m1",
                        () -> {
                            int cur = running.incrementAndGet();
                            maxRunning.updateAndGet((m) -> Math.max(m, cur));
                            Thread.sleep(80L + (idx % 3) * 20L);
                            running.decrementAndGet();
                            return "{\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}}";
                        },
                        queue::parseOpenAiUsageFromJson
                );
            }));
        }

        assertTrue(ready.await(3, TimeUnit.SECONDS));
        start.countDown();

        for (Future<String> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertTrue(maxRunning.get() <= 2, "maxRunning=" + maxRunning.get());
        assertTrue(queue.snapshot().recentCompleted().size() > 0);
        queue.shutdown();
    }

    @Test
    void shouldNotifyCompletedListeners() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(1000);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> seenId = new AtomicReference<>(null);
        queue.subscribeCompleted((snap) -> {
            if (snap == null) return;
            seenId.set(snap.getId());
            latch.countDown();
        });

        queue.call(
                LlmQueueTaskType.CHAT,
                "p1",
                "m1",
                () -> "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
                queue::parseOpenAiUsageFromJson
        );

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(seenId.get());
        queue.shutdown();
    }

    @Test
    void shouldParseUsageWithLooseFormats() {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(1000);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        var u1 = queue.parseOpenAiUsageFromJson("{\"usage\":{\"prompt_tokens\":\"10\",\"completion_tokens\":\"20\",\"total_tokens\":\"30\"}}");
        assertNotNull(u1);
        assertEquals(10, u1.promptTokens());
        assertEquals(20, u1.completionTokens());
        assertEquals(30, u1.totalTokens());

        var u2 = queue.parseOpenAiUsageFromJson("{\"usage\":{\"promptTokens\":10,\"completionTokens\":20,\"totalTokens\":30}}");
        assertNotNull(u2);
        assertEquals(10, u2.promptTokens());
        assertEquals(20, u2.completionTokens());
        assertEquals(30, u2.totalTokens());

        var u3 = queue.parseOpenAiUsageFromJson("{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}");
        assertNotNull(u3);
        assertEquals(10, u3.promptTokens());
        assertEquals(20, u3.completionTokens());
        assertEquals(30, u3.totalTokens());

        var u4 = queue.parseOpenAiUsageFromJson("{\"usage\":{\"prompt_tokens\":332,\"total_tokens\":38}}");
        assertNotNull(u4);
        assertEquals(332, u4.promptTokens());
        assertEquals(38, u4.completionTokens());
        assertEquals(370, u4.totalTokens());

        var u5 = queue.parseOpenAiUsageFromJson("{\"usage\":{\"prompt_tokens\":\"332\",\"total_tokens\":\"38\"}}");
        assertNotNull(u5);
        assertEquals(332, u5.promptTokens());
        assertEquals(38, u5.completionTokens());
        assertEquals(370, u5.totalTokens());

        var u6 = queue.parseOpenAiUsageFromJson("{\"usage\":{\"prompt_tokens\":332,\"completion_tokens\":0,\"total_tokens\":370}}");
        assertNotNull(u6);
        assertEquals(332, u6.promptTokens());
        assertEquals(38, u6.completionTokens());
        assertEquals(370, u6.totalTokens());
        queue.shutdown();
    }

    @Test
    void shouldDedupPendingTasksByKey() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(1000);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        AtomicInteger calls = new AtomicInteger(0);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        var f1 = queue.submitDedup(
                LlmQueueTaskType.CHAT,
                "p1",
                "m1",
                0,
                "dedup-1",
                (t) -> {
                    calls.incrementAndGet();
                    started.countDown();
                    release.await(2, TimeUnit.SECONDS);
                    return "{\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}}";
                },
                queue::parseOpenAiUsageFromJson
        );

        assertTrue(started.await(2, TimeUnit.SECONDS));

        List<java.util.concurrent.CompletableFuture<String>> more = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            more.add(queue.submitDedup(
                    LlmQueueTaskType.CHAT,
                    "p1",
                    "m1",
                    0,
                    "dedup-1",
                    (t) -> "{\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}}",
                    queue::parseOpenAiUsageFromJson
            ));
        }

        release.countDown();

        assertNotNull(f1.get(5, TimeUnit.SECONDS));
        for (var f : more) {
            assertNotNull(f.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, calls.get());
        queue.shutdown();
    }

        @Test
        void shouldSnapshotWithLimitsAndCompletedTruncation() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(2);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<String> f1 = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            1,
            (task) -> {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}";
            },
            queue::parseOpenAiUsageFromJson
        );

        CompletableFuture<String> f2 = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}";
            },
            queue::parseOpenAiUsageFromJson
        );

        CompletableFuture<String> f3 = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            -1,
            (task) -> "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
            queue::parseOpenAiUsageFromJson
        );

        assertTrue(started.await(2, TimeUnit.SECONDS));
        waitUntil(() -> queue.snapshot().pendingCount() >= 1, 2000, "pending task did not appear");

        var full = queue.snapshot(10, 10, 10);
        assertEquals(2, full.running().size());
        assertEquals(1, full.pending().size());

        var limitedRunning = queue.snapshot(1, 10, 10);
        assertEquals(1, limitedRunning.running().size());
        long minSeq = full.running().stream().mapToLong(LlmCallQueueService.TaskSnapshot::getSeq).min().orElseThrow();
        assertEquals(minSeq, limitedRunning.running().get(0).getSeq());

        var zeroLists = queue.snapshot(0, 0, 0);
        assertTrue(zeroLists.running().isEmpty());
        assertTrue(zeroLists.pending().isEmpty());
        assertTrue(zeroLists.recentCompleted().isEmpty());
        assertEquals(2, zeroLists.runningCount());
        assertEquals(1, zeroLists.pendingCount());

        release.countDown();
        assertNotNull(f1.get(5, TimeUnit.SECONDS));
        assertNotNull(f2.get(5, TimeUnit.SECONDS));
        assertNotNull(f3.get(5, TimeUnit.SECONDS));

        var doneAll = queue.snapshot(10, 10, 10);
        assertTrue(doneAll.recentCompleted().size() >= 3);
        var doneOne = queue.snapshot(10, 10, 1);
        assertEquals(1, doneOne.recentCompleted().size());

        queue.shutdown();
        }

        @Test
        void shouldHandleQueueFullAndRecordFailure() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(1);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<String> running = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}";
            },
            queue::parseOpenAiUsageFromJson
        );

        assertTrue(started.await(2, TimeUnit.SECONDS));

        CompletableFuture<String> pending = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
            queue::parseOpenAiUsageFromJson
        );

        CompletableFuture<String> rejected = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> "never",
            queue::parseOpenAiUsageFromJson
        );

        ExecutionException queueFull = assertThrows(ExecutionException.class, () -> rejected.get(2, TimeUnit.SECONDS));
        assertTrue(queueFull.getCause() instanceof IllegalStateException);
        assertTrue(queueFull.getCause().getMessage().contains("队列已满"));

        var snap = queue.snapshot(10, 10, 10);
        assertEquals(1, snap.pendingCount());
        assertTrue(snap.recentCompleted().stream().anyMatch((s) ->
            s.getStatus() == LlmQueueTaskStatus.FAILED && "queue_full".equals(s.getError())));

        release.countDown();
        assertNotNull(running.get(5, TimeUnit.SECONDS));
        assertNotNull(pending.get(5, TimeUnit.SECONDS));
        queue.shutdown();
        }

        @Test
        void shouldCancelPendingTaskInDispatchLoop() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<String> running = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}";
            },
            queue::parseOpenAiUsageFromJson
        );

        assertTrue(started.await(2, TimeUnit.SECONDS));

        CompletableFuture<String> cancelled = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
            queue::parseOpenAiUsageFromJson
        );
        assertTrue(cancelled.cancel(true));

        waitUntil(() -> queue.snapshot(10, 10, 10).recentCompleted().stream()
                .anyMatch((s) -> s.getStatus() == LlmQueueTaskStatus.CANCELLED),
            3000,
            "cancelled task was not observed");

        var snap = queue.snapshot(10, 10, 10);
        assertEquals(0, snap.pendingCount());
        assertTrue(snap.recentCompleted().stream().anyMatch((s) ->
            s.getStatus() == LlmQueueTaskStatus.CANCELLED && "cancelled".equals(s.getError())));

        release.countDown();
        assertNotNull(running.get(5, TimeUnit.SECONDS));
        queue.shutdown();
        }

        @Test
        void shouldFindTaskDetailAcrossRunningPendingAndCompleted() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> runningId = new AtomicReference<>(null);

        CompletableFuture<String> running = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                runningId.set(task.id());
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}";
            },
            queue::parseOpenAiUsageFromJson
        );
        assertTrue(started.await(2, TimeUnit.SECONDS));

        CompletableFuture<String> pending = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
            queue::parseOpenAiUsageFromJson
        );

        waitUntil(() -> queue.snapshot().pendingCount() >= 1, 2000, "pending task did not appear");
        String pendingId = queue.snapshot(10, 10, 10).pending().get(0).getId();

        var runningDetail = queue.findTaskDetail(runningId.get());
        assertNotNull(runningDetail);
        assertEquals(LlmQueueTaskStatus.RUNNING, runningDetail.status());

        var pendingDetail = queue.findTaskDetail(pendingId);
        assertNotNull(pendingDetail);
        assertEquals(LlmQueueTaskStatus.PENDING, pendingDetail.status());

        assertNull(queue.findTaskDetail("   "));

        release.countDown();
        assertNotNull(running.get(5, TimeUnit.SECONDS));
        assertNotNull(pending.get(5, TimeUnit.SECONDS));

        var completedDetail = queue.findTaskDetail(runningId.get());
        assertNotNull(completedDetail);
        assertEquals(LlmQueueTaskStatus.DONE, completedDetail.status());

        queue.shutdown();
        }

        @Test
        void shouldUpdateRunningTokensOutMonotonicallyAndComputeRate() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CountDownLatch reported = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> taskId = new AtomicReference<>(null);

        CompletableFuture<String> running = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                taskId.set(task.id());
                task.reportEstimatedTokensOut(-5);
                task.reportEstimatedTokensOut(3);
                task.reportEstimatedTokensOut(2);
                reported.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}";
            },
            queue::parseOpenAiUsageFromJson
        );

        assertTrue(reported.await(2, TimeUnit.SECONDS));
        var detail = queue.findTaskDetail(taskId.get());
        assertNotNull(detail);
        assertEquals(LlmQueueTaskStatus.RUNNING, detail.status());
        assertEquals(3, detail.tokensOut());
        assertEquals(3, detail.totalTokens());
        assertNotNull(detail.tokensPerSec());

        ReflectionTestUtils.invokeMethod(queue, "updateRunningTokensOut", "   ", 1);
        ReflectionTestUtils.invokeMethod(queue, "updateRunningTokensOut", "not-exist", 5);

        release.countDown();
        assertNotNull(running.get(5, TimeUnit.SECONDS));
        queue.shutdown();
        }

        @Test
        void shouldNormalizeOpenAiCompatUsageForEdgeCases() {
        LlmCallQueueService.UsageMetrics m1 = ReflectionTestUtils.invokeMethod(
            LlmCallQueueService.class,
            "normalizeOpenAiCompatUsage",
            100,
            null,
            20
        );
        assertNotNull(m1);
        assertEquals(100, m1.promptTokens());
        assertEquals(20, m1.completionTokens());
        assertEquals(120, m1.totalTokens());

        LlmCallQueueService.UsageMetrics m2 = ReflectionTestUtils.invokeMethod(
            LlmCallQueueService.class,
            "normalizeOpenAiCompatUsage",
            null,
            5,
            20
        );
        assertNotNull(m2);
        assertEquals(15, m2.promptTokens());
        assertEquals(5, m2.completionTokens());
        assertEquals(20, m2.totalTokens());

        LlmCallQueueService.UsageMetrics m3 = ReflectionTestUtils.invokeMethod(
            LlmCallQueueService.class,
            "normalizeOpenAiCompatUsage",
            10,
            0,
            50
        );
        assertNotNull(m3);
        assertEquals(10, m3.promptTokens());
        assertEquals(40, m3.completionTokens());
        assertEquals(50, m3.totalTokens());

        LlmCallQueueService.UsageMetrics m4 = ReflectionTestUtils.invokeMethod(
            LlmCallQueueService.class,
            "normalizeOpenAiCompatUsage",
            -1,
            -2,
            -3
        );
        assertNull(m4);

        LlmCallQueueService.UsageMetrics m5 = ReflectionTestUtils.invokeMethod(
            LlmCallQueueService.class,
            "normalizeOpenAiCompatUsage",
            1,
            2,
            3
        );
        assertNotNull(m5);
        assertEquals(1, m5.promptTokens());
        assertEquals(2, m5.completionTokens());
        assertEquals(3, m5.totalTokens());
        }

        @Test
        void shouldUseSupplierOverloadsForSubmitAndCallDedup() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(20);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CompletableFuture<String> f = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            () -> "{\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4,\"total_tokens\":7}}",
            queue::parseOpenAiUsageFromJson
        );
        assertNotNull(f.get(5, TimeUnit.SECONDS));

        String out = queue.callDedup(
            LlmQueueTaskType.CHAT,
            " p1 ",
            " m1 ",
            0,
            " key-1 ",
            () -> "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
            queue::parseOpenAiUsageFromJson
        );
        assertNotNull(out);

        CompletableFuture<String> d1 = queue.submitDedup(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            "same-k",
            () -> "ok-dedup",
            null
        );
        CompletableFuture<String> d2 = queue.submitDedup(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            "same-k",
            () -> "should-not-run",
            null
        );
        assertEquals("ok-dedup", d1.get(5, TimeUnit.SECONDS));
        assertEquals("ok-dedup", d2.get(5, TimeUnit.SECONDS));

        queue.shutdown();
        }

        @Test
        void shouldBackfillTokensInFromTotalWhenTokensOutMissing() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        String result = queue.call(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            () -> "ok",
            (res) -> new LlmCallQueueService.UsageMetrics(null, 0, 12, null)
        );
        assertEquals("ok", result);

        var snap = queue.snapshot(10, 10, 1);
        assertEquals(1, snap.recentCompleted().size());
        var done = snap.recentCompleted().get(0);
        assertEquals(12, done.getTokensIn());
        assertEquals(12, done.getTotalTokens());
        assertNull(done.getTokensOut());

        queue.shutdown();
        }

        @Test
        void shouldTrimCompletedAndDetailsByKeepLimit() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(1);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        AtomicReference<String> id1 = new AtomicReference<>(null);
        AtomicReference<String> id2 = new AtomicReference<>(null);

        queue.call(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                id1.set(task.id());
                return "ok-1";
            },
            null
        );
        queue.call(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                id2.set(task.id());
                return "ok-2";
            },
            null
        );

        var snap = queue.snapshot(10, 10, 10);
        assertEquals(1, snap.recentCompleted().size());
        assertNotNull(queue.findTaskDetail(id2.get()));
        assertNull(queue.findTaskDetail(id1.get()));

        queue.shutdown();
        }

        @Test
        void shouldDropCompletedWhenKeepCompletedIsZero() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(0);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        queue.call(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            () -> "ok",
            null
        );

        var snap = queue.snapshot(10, 10, 10);
        assertTrue(snap.recentCompleted().isEmpty());
        queue.shutdown();
        }

        @Test
        void shouldWrapNonExceptionCauseInAwait() {
        CompletableFuture<String> f = new CompletableFuture<>();
        AssertionError error = new AssertionError("boom");
        f.completeExceptionally(error);

        RuntimeException wrapped = assertThrows(
            RuntimeException.class,
            () -> ReflectionTestUtils.invokeMethod(LlmCallQueueService.class, "await", f)
        );
        assertSame(error, wrapped.getCause());
        }

        @Test
        void shouldCompleteWhenMetricsExtractorThrows() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        String out = queue.call(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            () -> "ok",
            (res) -> {
                throw new IllegalStateException("metrics parse failed");
            }
        );

        assertEquals("ok", out);
        var snap = queue.snapshot(10, 10, 1);
        assertEquals(1, snap.recentCompleted().size());
        assertEquals(LlmQueueTaskStatus.DONE, snap.recentCompleted().get(0).getStatus());
        assertNull(snap.recentCompleted().get(0).getTokensIn());
        assertNull(snap.recentCompleted().get(0).getTokensOut());
        queue.shutdown();
        }

        @Test
        void shouldUnsubscribeCompletedListener() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        AtomicInteger hits = new AtomicInteger(0);
        Runnable unsubscribe = queue.subscribeCompleted((snap) -> {
            if (snap != null) hits.incrementAndGet();
        });

        queue.call(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            () -> "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
            queue::parseOpenAiUsageFromJson
        );
        waitUntil(() -> hits.get() >= 1, 1500, "listener not called");

        unsubscribe.run();
        queue.call(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            () -> "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
            queue::parseOpenAiUsageFromJson
        );
        assertEquals(1, hits.get());
        queue.shutdown();
        }

        @Test
        void shouldReturnNullSnapshotWhenLockContended() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        ReentrantLock lock = (ReentrantLock) ReflectionTestUtils.getField(queue, "lock");
        assertNotNull(lock);

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                locked.countDown();
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        try {
            assertTrue(locked.await(2, TimeUnit.SECONDS));
            var snap = queue.trySnapshot(10, 10, 10, 10);
            assertNull(snap);
        } finally {
            release.countDown();
            holder.join(2000);
            queue.shutdown();
        }
        }

        @Test
        void shouldSupportNullCompletedListener() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        Runnable noopUnsubscribe = queue.subscribeCompleted(null);
        assertNotNull(noopUnsubscribe);
        noopUnsubscribe.run();

        String out = queue.call(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            () -> "ok",
            null
        );
        assertEquals("ok", out);
        queue.shutdown();
        }

        @Test
        void shouldClampRunningInputAndOutputDetails() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        String huge = "x".repeat(5_000_001);
        CountDownLatch reported = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> taskId = new AtomicReference<>(null);

        CompletableFuture<String> running = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                taskId.set(task.id());
                task.reportInput(huge);
                task.reportOutput(huge);
                reported.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "ok";
            },
            null
        );

        assertTrue(reported.await(2, TimeUnit.SECONDS));
        var detail = queue.findTaskDetail(taskId.get());
        assertNotNull(detail);
        assertNotNull(detail.input());
        assertNotNull(detail.output());
        assertTrue(detail.input().endsWith("...(truncated)..."));
        assertTrue(detail.output().endsWith("...(truncated)..."));

        release.countDown();
        assertEquals("ok", running.get(5, TimeUnit.SECONDS));
        queue.shutdown();
        }

        @Test
        void shouldBuildInFlightKeyWithTrimmedFallbacks() {
        String key = ReflectionTestUtils.invokeMethod(
            LlmCallQueueService.class,
            "buildInFlightKey",
            null,
            "  p1  ",
            "  ",
            null
        );
        assertEquals("UNKNOWN|p1||", key);

        String key2 = ReflectionTestUtils.invokeMethod(
            LlmCallQueueService.class,
            "buildInFlightKey",
            LlmQueueTaskType.CHAT,
            null,
            " m1 ",
            " d1 "
        );
        assertEquals("CHAT||m1|d1", key2);
        }

        @Test
        void shouldUseEstimatedCompletionTokensWhenCompletionTokensMissing() {
        LlmCallQueueService.UsageMetrics m = new LlmCallQueueService.UsageMetrics(1, 0, 10, 7);
        assertEquals(7, m.tokensOut());
        }

        @Test
        void shouldCreateTaskWithNullTypeAsUnknown() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        queue.call(
            null,
            "p1",
            "m1",
            () -> "ok",
            null
        );

        var snap = queue.snapshot(10, 10, 1);
        assertEquals(1, snap.recentCompleted().size());
        assertEquals(LlmQueueTaskType.UNKNOWN, snap.recentCompleted().get(0).getType());
        queue.shutdown();
        }

        @Test
        void shouldExposeSnapshotDateTimeAccessorsForPendingAndCompleted() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<String> running = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "ok";
            },
            null
        );

        assertTrue(started.await(2, TimeUnit.SECONDS));
        CompletableFuture<String> pending = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> "ok-2",
            null
        );

        waitUntil(() -> queue.snapshot(10, 10, 10).pendingCount() >= 1, 2000, "pending task did not appear");
        var pendingSnap = queue.snapshot(10, 10, 10).pending().get(0);
        assertNotNull(pendingSnap.createdAt());
        assertNull(pendingSnap.startedAt());
        assertNull(pendingSnap.finishedAt());

        release.countDown();
        assertEquals("ok", running.get(5, TimeUnit.SECONDS));
        assertEquals("ok-2", pending.get(5, TimeUnit.SECONDS));

        var done = queue.snapshot(10, 10, 1).recentCompleted().get(0);
        assertNotNull(done.createdAt());
        assertNotNull(done.startedAt());
        assertNotNull(done.finishedAt());
        queue.shutdown();
        }

        @Test
        void shouldExposeTaskDetailDateTimeAccessorsForPending() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "ok";
            },
            null
        );
        assertTrue(started.await(2, TimeUnit.SECONDS));

        queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> "ok-2",
            null
        );

        waitUntil(() -> queue.snapshot(10, 10, 10).pendingCount() >= 1, 2000, "pending task did not appear");
        String pendingId = queue.snapshot(10, 10, 10).pending().get(0).getId();
        var pendingDetail = queue.findTaskDetail(pendingId);
        assertNotNull(pendingDetail);
        assertNotNull(pendingDetail.createdAt());
        assertNull(pendingDetail.startedAt());
        assertNull(pendingDetail.finishedAt());

        release.countDown();
        queue.shutdown();
        }

        @Test
        void shouldCoverUpdateRunningInputOutputModelBranches() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(2);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch reported = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> id1 = new AtomicReference<>(null);

        CompletableFuture<String> f1 = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                id1.set(task.id());
                started.countDown();
                task.reportInput("in-1");
                task.reportOutput("out-1");
                task.reportModel("  gpt-4o  ");
                task.reportModel("   ");
                reported.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "ok-1";
            },
            null
        );

        CompletableFuture<String> f2 = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m2",
            0,
            (task) -> {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "ok-2";
            },
            null
        );

        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertTrue(reported.await(2, TimeUnit.SECONDS));

        ReflectionTestUtils.invokeMethod(queue, "updateRunningInput", "   ", "x");
        ReflectionTestUtils.invokeMethod(queue, "updateRunningOutput", "", "x");
        ReflectionTestUtils.invokeMethod(queue, "updateRunningModel", "   ", "m");

        ReentrantLock lock = (ReentrantLock) ReflectionTestUtils.getField(queue, "lock");
        assertNotNull(lock);
        @SuppressWarnings("unchecked")
        List<Object> running = (List<Object>) ReflectionTestUtils.getField(queue, "running");
        assertNotNull(running);

        lock.lock();
        try {
            running.add(null);
        } finally {
            lock.unlock();
        }

        var d = queue.findTaskDetail(id1.get());
        assertNotNull(d);
        assertEquals("gpt-4o", d.model());
        assertEquals("in-1", d.input());
        assertEquals("out-1", d.output());

        release.countDown();
        assertEquals("ok-1", f1.get(5, TimeUnit.SECONDS));
        assertEquals("ok-2", f2.get(5, TimeUnit.SECONDS));
        queue.shutdown();
        }

        @Test
        void shouldReturnNullWhenClampDetailInputIsNull() {
        String out = ReflectionTestUtils.invokeMethod(LlmCallQueueService.class, "clampDetail", (String) null);
        assertNull(out);
        }

        @Test
        void shouldParseUsageForInvalidJsonAndNonObjectRoot() {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        assertNull(queue.parseOpenAiUsageFromJson(null));
        assertNull(queue.parseOpenAiUsageFromJson("   "));
        assertNull(queue.parseOpenAiUsageFromJson("{bad-json"));
        assertNull(queue.parseOpenAiUsageFromJson("[1,2,3]"));

        var u1 = queue.parseOpenAiUsageFromJson("{\"usage\":1,\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12}");
        assertNotNull(u1);
        assertEquals(10, u1.promptTokens());
        assertEquals(2, u1.completionTokens());
        assertEquals(12, u1.totalTokens());

        var u2 = queue.parseOpenAiUsageFromJson("{\"usage\":{\"prompt_tokens\":\"12.7\",\"completion_tokens\":\"1\",\"total_tokens\":\"13\"}}");
        assertNotNull(u2);
        assertEquals(12, u2.promptTokens());
        assertEquals(1, u2.completionTokens());
        assertEquals(13, u2.totalTokens());

        var u3 = queue.parseOpenAiUsageFromJson("{\"usage\":{\"prompt_tokens\":\"10a\",\"total_tokens\":3}}");
        assertNotNull(u3);
        assertNull(u3.promptTokens());
        assertNull(u3.completionTokens());
        assertEquals(3, u3.totalTokens());
        queue.shutdown();
        }

        @Test
        void shouldNormalizeOpenAiCompatUsageForMissingBranches() {
        LlmCallQueueService.UsageMetrics tLessThanP = ReflectionTestUtils.invokeMethod(
            LlmCallQueueService.class,
            "normalizeOpenAiCompatUsage",
            10,
            2,
            5
        );
        assertNotNull(tLessThanP);
        assertEquals(10, tLessThanP.promptTokens());
        assertEquals(5, tLessThanP.completionTokens());
        assertEquals(15, tLessThanP.totalTokens());

        LlmCallQueueService.UsageMetrics onlyPC = ReflectionTestUtils.invokeMethod(
            LlmCallQueueService.class,
            "normalizeOpenAiCompatUsage",
            10,
            2,
            null
        );
        assertNotNull(onlyPC);
        assertEquals(10, onlyPC.promptTokens());
        assertEquals(2, onlyPC.completionTokens());
        assertEquals(12, onlyPC.totalTokens());

        LlmCallQueueService.UsageMetrics pAndT = ReflectionTestUtils.invokeMethod(
            LlmCallQueueService.class,
            "normalizeOpenAiCompatUsage",
            10,
            null,
            20
        );
        assertNotNull(pAndT);
        assertEquals(10, pAndT.promptTokens());
        assertEquals(10, pAndT.completionTokens());
        assertEquals(20, pAndT.totalTokens());
        }

        @Test
        void shouldFallbackToNormalSubmitWhenDedupKeyBlank() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        AtomicInteger calls = new AtomicInteger(0);
        CompletableFuture<String> f1 = queue.submitDedup(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            "   ",
            (t) -> {
                calls.incrementAndGet();
                return "ok";
            },
            null
        );
        CompletableFuture<String> f2 = queue.submitDedup(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            "",
            (t) -> {
                calls.incrementAndGet();
                return "ok";
            },
            null
        );
        assertEquals("ok", f1.get(5, TimeUnit.SECONDS));
        assertEquals("ok", f2.get(5, TimeUnit.SECONDS));
        assertEquals(2, calls.get());
        queue.shutdown();
        }

        @Test
        void shouldReuseRecentDedupAfterCompletionAndEvictWhenOverKeep() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(1);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        AtomicInteger calls = new AtomicInteger(0);

        CompletableFuture<String> first = queue.submitDedup(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            "k1",
            (t) -> {
                calls.incrementAndGet();
                return "r1";
            },
            null
        );
        assertEquals("r1", first.get(5, TimeUnit.SECONDS));

        CompletableFuture<String> reused = queue.submitDedup(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            "k1",
            (t) -> {
                calls.incrementAndGet();
                return "should-not-run";
            },
            null
        );
        assertEquals("r1", reused.get(5, TimeUnit.SECONDS));
        assertEquals(1, calls.get());

        CompletableFuture<String> secondKey = queue.submitDedup(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            "k2",
            (t) -> {
                calls.incrementAndGet();
                return "r2";
            },
            null
        );
        assertEquals("r2", secondKey.get(5, TimeUnit.SECONDS));

        CompletableFuture<String> k1Again = queue.submitDedup(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            "k1",
            (t) -> {
                calls.incrementAndGet();
                return "r3";
            },
            null
        );
        assertEquals("r3", k1Again.get(5, TimeUnit.SECONDS));
        assertEquals(3, calls.get());
        queue.shutdown();
        }

        @Test
        void shouldCoverRecordRecentDedupEvictionAndGuardBranches() {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(1);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        ReflectionTestUtils.invokeMethod(queue, "recordRecentDedup", "   ", new CompletableFuture<>());
        ReflectionTestUtils.invokeMethod(queue, "recordRecentDedup", "k0", null);

        CompletableFuture<Object> f1 = new CompletableFuture<>();
        CompletableFuture<Object> f2 = new CompletableFuture<>();
        ReflectionTestUtils.invokeMethod(queue, "recordRecentDedup", "k1", f1);
        ReflectionTestUtils.invokeMethod(queue, "recordRecentDedup", "k2", f2);

        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, CompletableFuture<Object>> recentDedup =
            (java.util.concurrent.ConcurrentHashMap<String, CompletableFuture<Object>>) ReflectionTestUtils.getField(queue, "recentDedup");
        assertNotNull(recentDedup);
        assertNull(recentDedup.get("k1"));
        assertSame(f2, recentDedup.get("k2"));
        queue.shutdown();
        }

        @Test
        void shouldCoverBuildInFlightKeyModelNullBranch() {
        String key = ReflectionTestUtils.invokeMethod(
            LlmCallQueueService.class,
            "buildInFlightKey",
            LlmQueueTaskType.CHAT,
            "p1",
            null,
            "d1"
        );
        assertEquals("CHAT|p1||d1", key);
        }

        @Test
        void shouldInterruptAwaitAndCallAndCallDedupPaths() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CompletableFuture<String> never = new CompletableFuture<>();
        Method await = LlmCallQueueService.class.getDeclaredMethod("await", CompletableFuture.class);
        await.setAccessible(true);

        CountDownLatch awaiting = new CountDownLatch(1);
        AtomicReference<Throwable> awaitErr = new AtomicReference<>(null);
        Thread t1 = new Thread(() -> {
            awaiting.countDown();
            try {
                await.invoke(null, never);
            } catch (InvocationTargetException ite) {
                awaitErr.set(ite.getCause());
            } catch (Exception e) {
                awaitErr.set(e);
            }
        });
        t1.start();
        assertTrue(awaiting.await(2, TimeUnit.SECONDS));
        t1.interrupt();
        t1.join(2000);
        assertTrue(awaitErr.get() instanceof InterruptedException, String.valueOf(awaitErr.get()));

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> callErr = new AtomicReference<>(null);
        AtomicLong callInterrupted = new AtomicLong(0);
        Thread t2 = new Thread(() -> {
            try {
                queue.call(
                    LlmQueueTaskType.CHAT,
                    "p1",
                    "m1",
                    0,
                    (task) -> {
                        started.countDown();
                        release.await(2, TimeUnit.SECONDS);
                        return "ok";
                    },
                    null
                );
            } catch (Throwable e) {
                callErr.set(e);
                if (Thread.currentThread().isInterrupted()) callInterrupted.incrementAndGet();
            }
        });
        t2.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        t2.interrupt();
        release.countDown();
        t2.join(3000);
        assertTrue(callErr.get() instanceof IllegalStateException);
        assertTrue(callErr.get().getMessage().contains("等待 LLM 调用结果被中断"));
        assertEquals(1, callInterrupted.get());

        CountDownLatch started2 = new CountDownLatch(1);
        CountDownLatch release2 = new CountDownLatch(1);
        AtomicReference<Throwable> callDedupErr = new AtomicReference<>(null);
        AtomicLong callDedupInterrupted = new AtomicLong(0);
        Thread t3 = new Thread(() -> {
            try {
                queue.callDedup(
                    LlmQueueTaskType.CHAT,
                    "p1",
                    "m1",
                    0,
                    "dk1",
                    (task) -> {
                        started2.countDown();
                        release2.await(2, TimeUnit.SECONDS);
                        return "ok";
                    },
                    null
                );
            } catch (Throwable e) {
                callDedupErr.set(e);
                if (Thread.currentThread().isInterrupted()) callDedupInterrupted.incrementAndGet();
            }
        });
        t3.start();
        assertTrue(started2.await(2, TimeUnit.SECONDS));
        t3.interrupt();
        release2.countDown();
        t3.join(3000);
        assertTrue(callDedupErr.get() instanceof IllegalStateException);
        assertTrue(callDedupErr.get().getMessage().contains("等待去重任务结果被中断"));
        assertEquals(1, callDedupInterrupted.get());

        queue.shutdown();
        }

        @Test
        void shouldReturnNullWhenTrySnapshotInterruptedWhileWaitingForLock() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        ReentrantLock lock = (ReentrantLock) ReflectionTestUtils.getField(queue, "lock");
        assertNotNull(lock);

        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<LlmCallQueueService.QueueSnapshot> out = new AtomicReference<>(null);
        AtomicLong interrupted = new AtomicLong(0);

        lock.lock();
        try {
            Thread t = new Thread(() -> {
                entered.countDown();
                var snap = queue.trySnapshot(10, 10, 10, 5000);
                out.set(snap);
                if (Thread.currentThread().isInterrupted()) interrupted.incrementAndGet();
            });
            t.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            Thread.sleep(50);
            t.interrupt();
            t.join(3000);
        } finally {
            lock.unlock();
            queue.shutdown();
        }

        assertNull(out.get());
        assertEquals(1, interrupted.get());
        }

        @Test
        void shouldSwallowListenerExceptionsAndStillNotifyOthers() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        queue.subscribeCompleted((snap) -> {
            throw new IllegalStateException("listener boom");
        });
        CountDownLatch ok = new CountDownLatch(1);
        queue.subscribeCompleted((snap) -> ok.countDown());

        queue.call(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            () -> "ok",
            null
        );
        assertTrue(ok.await(2, TimeUnit.SECONDS));
        queue.shutdown();
        }

        @Test
        void shouldPersistCompletedAndSwallowRepositoryException() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        Mockito.when(repo.save(Mockito.any())).thenThrow(new IllegalStateException("db down"));
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        String out = queue.call(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            () -> "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
            queue::parseOpenAiUsageFromJson
        );
        assertNotNull(out);
        assertEquals(LlmQueueTaskStatus.DONE, queue.snapshot(10, 10, 1).recentCompleted().get(0).getStatus());
        queue.shutdown();
        }

        @Test
        void shouldPersistCompletedWithExpectedFields() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        String out = queue.call(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                task.reportInput("in");
                task.reportOutput("out");
                return "ok";
            },
            (r) -> new LlmCallQueueService.UsageMetrics(1, 2, 3, null)
        );
        assertEquals("ok", out);

        ArgumentCaptor<com.example.EnterpriseRagCommunity.entity.monitor.LlmQueueTaskHistoryEntity> cap =
            ArgumentCaptor.forClass(com.example.EnterpriseRagCommunity.entity.monitor.LlmQueueTaskHistoryEntity.class);
        Mockito.verify(repo, Mockito.atLeastOnce()).save(cap.capture());
        var saved = cap.getValue();
        assertNotNull(saved.getTaskId());
        assertEquals(LlmQueueTaskType.CHAT, saved.getType());
        assertEquals(LlmQueueTaskStatus.DONE, saved.getStatus());
        assertEquals("p1", saved.getProviderId());
        assertEquals("m1", saved.getModel());
        assertEquals(1, saved.getTokensIn());
        assertEquals(2, saved.getTokensOut());
        assertEquals(3, saved.getTotalTokens());
        assertEquals("in", saved.getInput());
        assertEquals("out", saved.getOutput());

        queue.shutdown();
        }

        @Test
        void shouldCoverApplyMetricsAndFinishFailureNullExceptionBranches() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        ReflectionTestUtils.invokeMethod(queue, "applyMetrics", null, new LlmCallQueueService.UsageMetrics(1, 2, 3, null));
        ReflectionTestUtils.invokeMethod(queue, "pushCompletedDetail", (Object) null);
        ReflectionTestUtils.invokeMethod(queue, "notifyCompleted", (Object) null);
        ReflectionTestUtils.invokeMethod(queue, "persistCompleted", (Object) null);

        Method createTask = LlmCallQueueService.class.getDeclaredMethod(
            "createTask",
            LlmQueueTaskType.class,
            String.class,
            String.class,
            int.class,
            String.class,
            Class.forName("com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService$CheckedTaskSupplier"),
            Class.forName("com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService$ResultMetricsExtractor"),
            CompletableFuture.class
        );
        createTask.setAccessible(true);

        CompletableFuture<Object> future = new CompletableFuture<>();
        Object task = createTask.invoke(
            queue,
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            null,
            null,
            null,
            future
        );

        ReflectionTestUtils.invokeMethod(queue, "finishFailure", task, null);
        assertEquals(LlmQueueTaskStatus.FAILED, queue.snapshot(10, 10, 1).recentCompleted().get(0).getStatus());
        assertEquals("error", queue.snapshot(10, 10, 1).recentCompleted().get(0).getError());

        queue.shutdown();
        }

        @Test
        void shouldCoverExecuteTaskWithNullSupplierBranch() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        Class<?> checkedTaskSupplier = Class.forName("com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService$CheckedTaskSupplier");
        Class<?> resultMetricsExtractor = Class.forName("com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService$ResultMetricsExtractor");
        Class<?> taskClass = Class.forName("com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService$Task");

        var ctor = taskClass.getDeclaredConstructor(
            String.class,
            long.class,
            int.class,
            LlmQueueTaskType.class,
            String.class,
            String.class,
            String.class,
            long.class,
            checkedTaskSupplier,
            resultMetricsExtractor,
            CompletableFuture.class
        );
        ctor.setAccessible(true);

        CompletableFuture<Object> future = new CompletableFuture<>();
        Object task = ctor.newInstance(
            "tid-1",
            1L,
            0,
            LlmQueueTaskType.CHAT,
            null,
            "p1",
            "m1",
            System.currentTimeMillis(),
            null,
            null,
            future
        );

        ReflectionTestUtils.invokeMethod(queue, "executeTask", task);
        assertNull(future.get(5, TimeUnit.SECONDS));
        assertEquals(LlmQueueTaskStatus.DONE, queue.snapshot(10, 10, 1).recentCompleted().get(0).getStatus());
        queue.shutdown();
        }

        @Test
        void shouldCoverUpdateRunningTokensOutWhenTokensInPresent() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> id = new AtomicReference<>(null);

        CompletableFuture<String> f = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                id.set(task.id());
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "ok";
            },
            null
        );

        assertTrue(started.await(2, TimeUnit.SECONDS));

        ReentrantLock lock = (ReentrantLock) ReflectionTestUtils.getField(queue, "lock");
        assertNotNull(lock);
        @SuppressWarnings("unchecked")
        List<Object> running = (List<Object>) ReflectionTestUtils.getField(queue, "running");
        assertNotNull(running);

        lock.lock();
        try {
            Object t0 = running.stream().filter((o) -> o != null).findFirst().orElseThrow();
            ReflectionTestUtils.setField(t0, "tokensIn", 7);
            running.add(null);
        } finally {
            lock.unlock();
        }

        ReflectionTestUtils.invokeMethod(queue, "updateRunningTokensOut", id.get(), 3);
        var d = queue.findTaskDetail(id.get());
        assertNotNull(d);
        assertEquals(10, d.totalTokens());

        release.countDown();
        assertEquals("ok", f.get(5, TimeUnit.SECONDS));
        queue.shutdown();
        }

        @Test
        void shouldCoverEnsureDispatcherStartedStoppedBranch() {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        java.util.concurrent.atomic.AtomicBoolean stopped =
            (java.util.concurrent.atomic.AtomicBoolean) ReflectionTestUtils.getField(queue, "stopped");
        assertNotNull(stopped);
        stopped.set(true);
        ReflectionTestUtils.invokeMethod(queue, "ensureDispatcherStarted");
        queue.shutdown();
        }

        @Test
        void shouldThrowOriginalExceptionFromAwaitWhenCauseIsException() throws Exception {
        Method await = LlmCallQueueService.class.getDeclaredMethod("await", CompletableFuture.class);
        await.setAccessible(true);

        CompletableFuture<String> f = new CompletableFuture<>();
        IllegalArgumentException ex = new IllegalArgumentException("boom");
        f.completeExceptionally(ex);

        try {
            await.invoke(null, f);
            fail("should throw");
        } catch (InvocationTargetException ite) {
            assertSame(ex, ite.getCause());
        }
        }

        @Test
        void shouldCoverAsIntLooseAndPickIntLooseBranches() throws Exception {
        Method asIntLoose = LlmCallQueueService.class.getDeclaredMethod("asIntLoose", JsonNode.class);
        asIntLoose.setAccessible(true);
        Method pickIntLoose = LlmCallQueueService.class.getDeclaredMethod("pickIntLoose", JsonNode.class, String[].class);
        pickIntLoose.setAccessible(true);

        assertNull(asIntLoose.invoke(null, new Object[]{null}));
        assertEquals(7, asIntLoose.invoke(null, IntNode.valueOf(7)));
        assertNull(asIntLoose.invoke(null, TextNode.valueOf("   ")));
        assertEquals(12, asIntLoose.invoke(null, TextNode.valueOf("12")));
        assertEquals(12, asIntLoose.invoke(null, TextNode.valueOf("12.7")));

        JsonNode mocked = Mockito.mock(JsonNode.class);
        Mockito.when(mocked.isMissingNode()).thenReturn(false);
        Mockito.when(mocked.isNull()).thenReturn(false);
        Mockito.when(mocked.isNumber()).thenReturn(false);
        Mockito.when(mocked.isTextual()).thenReturn(true);
        Mockito.when(mocked.asText()).thenReturn(null);
        assertNull(asIntLoose.invoke(null, mocked));

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = mapper.createObjectNode();
        obj.put("a", 1);
        assertNull(pickIntLoose.invoke(null, new Object[]{null, new String[]{"a"}}));
        assertNull(pickIntLoose.invoke(null, new Object[]{obj, (String[]) null}));
        assertEquals(1, pickIntLoose.invoke(null, new Object[]{obj, new String[]{null, " ", "a"}}));
        assertNull(pickIntLoose.invoke(null, new Object[]{obj, new String[]{}}));
        }

        @Test
        void shouldCoverClampDetailShortStringBranch() {
        String out = ReflectionTestUtils.invokeMethod(LlmCallQueueService.class, "clampDetail", "x");
        assertEquals("x", out);
        }

        @Test
        void shouldCoverDispatchLoopInterruptedWhileWaiting() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<String> running = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "ok";
            },
            null
        );
        assertTrue(started.await(2, TimeUnit.SECONDS));

        CompletableFuture<String> pending = queue.submit(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            (task) -> "never",
            null
        );
        waitUntil(() -> queue.snapshot(10, 10, 10).pendingCount() >= 1, 2000, "pending task did not appear");

        java.util.concurrent.atomic.AtomicBoolean stopped =
            (java.util.concurrent.atomic.AtomicBoolean) ReflectionTestUtils.getField(queue, "stopped");
        Thread dispatcherThread = (Thread) ReflectionTestUtils.getField(queue, "dispatcherThread");
        assertNotNull(stopped);
        assertNotNull(dispatcherThread);

        stopped.set(true);
        dispatcherThread.interrupt();
        dispatcherThread.join(2000);

        pending.cancel(true);
        release.countDown();
        assertEquals("ok", running.get(5, TimeUnit.SECONDS));
        queue.shutdown();
        }

        @Test
        void shouldCoverTaskOrderComparatorBranches() throws Exception {
        Class<?> checkedTaskSupplier = Class.forName("com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService$CheckedTaskSupplier");
        Class<?> resultMetricsExtractor = Class.forName("com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService$ResultMetricsExtractor");
        Class<?> taskClass = Class.forName("com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService$Task");

        java.lang.reflect.Constructor<?> ctor = taskClass.getDeclaredConstructor(
            String.class,
            long.class,
            int.class,
            LlmQueueTaskType.class,
            String.class,
            String.class,
            String.class,
            long.class,
            checkedTaskSupplier,
            resultMetricsExtractor,
            CompletableFuture.class
        );
        ctor.setAccessible(true);

        Object t1 = ctor.newInstance("t1", 1L, 0, LlmQueueTaskType.CHAT, null, "p1", "m1", System.currentTimeMillis(), null, null, new CompletableFuture<>());
        Object t2 = ctor.newInstance("t2", 2L, 0, LlmQueueTaskType.CHAT, null, "p1", "m1", System.currentTimeMillis(), null, null, new CompletableFuture<>());
        Object tHigh = ctor.newInstance("t3", 3L, 5, LlmQueueTaskType.CHAT, null, "p1", "m1", System.currentTimeMillis(), null, null, new CompletableFuture<>());

        @SuppressWarnings("unchecked")
        java.util.Comparator<Object> cmp = (java.util.Comparator<Object>) ReflectionTestUtils.getField(LlmCallQueueService.class, "TASK_ORDER");
        assertNotNull(cmp);

        assertTrue(cmp.compare(t1, tHigh) > 0);
        assertTrue(cmp.compare(t1, t2) < 0);
        }

        @Test
        void shouldReuseInFlightFutureWithoutExecutingSupplierAgain() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentMap<String, CompletableFuture<Object>> inFlight =
            (java.util.concurrent.ConcurrentMap<String, CompletableFuture<Object>>) ReflectionTestUtils.getField(queue, "inFlight");
        assertNotNull(inFlight);

        CompletableFuture<Object> existing = CompletableFuture.completedFuture("reused");
        inFlight.put("CHAT|p1|m1|dedup-branch", existing);

        AtomicInteger supplierCalls = new AtomicInteger(0);
        CompletableFuture<String> out = queue.submitDedup(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            "dedup-branch",
            (task) -> {
                supplierCalls.incrementAndGet();
                return "fresh";
            },
            null
        );

        assertSame(existing, out);
        assertEquals("reused", out.get(2, TimeUnit.SECONDS));
        assertEquals(0, supplierCalls.get());
        queue.shutdown();
        }

        @Test
        void shouldAllowRetryAfterFailureWhenKeepCompletedZero() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(0);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);

        AtomicInteger attempts = new AtomicInteger(0);
        CompletableFuture<String> first = queue.submitDedup(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            "retry-key",
            (task) -> {
                int n = attempts.incrementAndGet();
                throw new IllegalStateException("boom-" + n);
            },
            null
        );

        ExecutionException firstErr = assertThrows(ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS));
        assertTrue(firstErr.getCause() instanceof IllegalStateException);
        assertEquals("boom-1", firstErr.getCause().getMessage());

        CompletableFuture<String> second = queue.submitDedup(
            LlmQueueTaskType.CHAT,
            "p1",
            "m1",
            0,
            "retry-key",
            (task) -> "ok-" + attempts.incrementAndGet(),
            null
        );

        assertEquals("ok-2", second.get(5, TimeUnit.SECONDS));
        assertEquals(2, attempts.get());
        queue.shutdown();
        }
}
