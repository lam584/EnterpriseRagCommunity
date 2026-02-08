package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmQueueTaskHistoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LlmCallQueueServiceTest {

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
    }
}
