package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmQueueTaskHistoryRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LlmLoadBalanceTimeseriesServiceTest {

    @Test
    void shouldAggregateCountsErrorsAnd429() throws Exception {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setMaxConcurrent(1);
        props.setMaxQueueSize(10_000);
        props.setKeepCompleted(10);

        LlmQueueTaskHistoryRepository repo = Mockito.mock(LlmQueueTaskHistoryRepository.class);
        LlmCallQueueService queue = new LlmCallQueueService(props, repo);
        LlmLoadBalanceTimeseriesService svc = new LlmLoadBalanceTimeseriesService(queue);

        queue.call(
                LlmQueueTaskType.CHAT,
                "p1",
                "m1",
                () -> "{\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}",
                queue::parseOpenAiUsageFromJson
        );

        try {
            queue.call(
                    LlmQueueTaskType.CHAT,
                    "p1",
                    "m1",
                    () -> {
                        throw new RuntimeException("HTTP 429 Too Many Requests");
                    },
                    queue::parseOpenAiUsageFromJson
            );
        } catch (Exception ignored) {
        }

        long now = System.currentTimeMillis();
        LlmLoadBalanceTimeseriesService.QueryResult qr = svc.query(now - 5 * 60_000L, now, 12);
        List<LlmLoadBalanceTimeseriesService.ModelSeries> models = qr.models();
        assertTrue(models.size() >= 1);

        LlmLoadBalanceTimeseriesService.ModelSeries m = models.stream()
                .filter((x) -> "p1".equals(x.providerId()) && "m1".equals(x.modelName()))
                .findFirst()
                .orElse(null);
        assertTrue(m != null);
        assertEquals(2L, m.count());
        assertEquals(1L, m.errorCount());
        assertEquals(1L, m.throttled429Count());
    }
}
