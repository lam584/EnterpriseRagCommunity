package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;

import java.time.Duration;
import java.time.LocalDateTime;

public final class ModerationPipelineRunSupport {

    private ModerationPipelineRunSupport() {
    }

    public static void sealRunningPipelineRun(Long queueId, ModerationPipelineRunRepository repository) {
        if (queueId == null || repository == null) {
            return;
        }
        ModerationPipelineRunEntity run = null;
        try {
            run = repository.findFirstByQueueIdOrderByCreatedAtDesc(queueId).orElse(null);
        } catch (Exception ignore) {
        }
        if (run == null || run.getStatus() != ModerationPipelineRunEntity.RunStatus.RUNNING) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            run.setStatus(ModerationPipelineRunEntity.RunStatus.FAIL);
            run.setFinalDecision(ModerationPipelineRunEntity.FinalDecision.HUMAN);
            run.setErrorCode("REQUEUED");
            run.setErrorMessage("Requeued to auto");
            run.setEndedAt(now);
            if (run.getStartedAt() != null) {
                run.setTotalMs(Duration.between(run.getStartedAt(), now).toMillis());
            }
            repository.save(run);
        } catch (Exception ignore) {
        }
    }
}
