package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModerationAutoKickService {
    private final ModerationQueueRepository queueRepository;
    private final ModerationRuleAutoRunner ruleRunner;
    private final ModerationVecAutoRunner vecRunner;
    private final ModerationLlmAutoRunner llmRunner;

    @Async("aiExecutor")
    public void kickQueueId(Long queueId) {
        if (queueId == null) return;
        for (int i = 0; i < 8; i++) {
            ModerationQueueEntity q;
            try {
                q = queueRepository.findById(queueId).orElse(null);
            } catch (Exception e) {
                return;
            }
            if (q == null) return;

            QueueStatus status = q.getStatus();
            if (status == QueueStatus.APPROVED || status == QueueStatus.REJECTED || status == QueueStatus.HUMAN) return;

            QueueStage stage = q.getCurrentStage();
            if (stage == QueueStage.RULE) {
                try {
                    ruleRunner.runForQueueId(queueId);
                } catch (Exception ignore) {
                }
                continue;
            }
            if (stage == QueueStage.VEC) {
                try {
                    vecRunner.runForQueueId(queueId);
                } catch (Exception ignore) {
                }
                continue;
            }
            if (stage == QueueStage.LLM) {
                try {
                    llmRunner.runForQueueId(queueId);
                } catch (Exception ignore) {
                }
                continue;
            }
            return;
        }
    }
}
