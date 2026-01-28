package com.example.EnterpriseRagCommunity.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class AiPostSummaryTriggerService {

    private final AiPostSummaryService aiPostSummaryService;

    public void scheduleGenerateAfterCommit(Long postId, Long actorUserId) {
        if (postId == null) return;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aiPostSummaryService.generateForPostIdAsync(postId, actorUserId);
                }
            });
            return;
        }

        aiPostSummaryService.generateForPostIdAsync(postId, actorUserId);
    }
}

