package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.monitor.LlmQueueTaskHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmQueueTaskHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class LlmCallQueueServiceReportModelTest {

    @Autowired
    LlmCallQueueService llmCallQueueService;

    @Autowired
    LlmQueueTaskHistoryRepository llmQueueTaskHistoryRepository;

    @Test
    void reportModelShouldPersistToHistoryModelField() throws Exception {
        AtomicReference<String> taskIdRef = new AtomicReference<>(null);
        String model = "qwen-plus-2025-04-28";

        String out = llmCallQueueService.call(
                LlmQueueTaskType.MODERATION_CHUNK,
                null,
                null,
                0,
                (task) -> {
                    taskIdRef.set(task.id());
                    task.reportModel(model);
                    return "ok-" + UUID.randomUUID();
                },
                (_res) -> new LlmCallQueueService.UsageMetrics(10, 1, 11, null)
        );
        assertNotNull(out);

        String taskId = taskIdRef.get();
        assertNotNull(taskId);

        LlmQueueTaskHistoryEntity e = llmQueueTaskHistoryRepository.findById(taskId).orElse(null);
        assertNotNull(e);
        assertEquals(model, e.getModel());
    }
}

