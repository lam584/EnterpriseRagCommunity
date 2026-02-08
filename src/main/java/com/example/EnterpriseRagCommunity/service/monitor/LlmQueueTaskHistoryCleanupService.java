package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmQueueTaskHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LlmQueueTaskHistoryCleanupService {

    private final LlmQueueProperties props;
    private final LlmQueueTaskHistoryRepository llmQueueTaskHistoryRepository;

    @Scheduled(fixedDelay = 3600_000)
    public void cleanup() {
        int keepDays = props.getHistoryKeepDays();
        if (keepDays <= 0) return;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(keepDays);
        llmQueueTaskHistoryRepository.deleteByFinishedAtBefore(cutoff);
    }
}
