package com.example.EnterpriseRagCommunity.service.moderation;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class ModerationAutoKickSupport {

    private ModerationAutoKickSupport() {
    }

    public static void kickQueueIdAfterCommit(ModerationAutoKickService moderationAutoKickService, Long queueId) {
        if (moderationAutoKickService == null || queueId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kickQuietly(moderationAutoKickService, queueId);
                }
            });
            return;
        }
        kickQuietly(moderationAutoKickService, queueId);
    }

    private static void kickQuietly(ModerationAutoKickService moderationAutoKickService, Long queueId) {
        try {
            moderationAutoKickService.kickQueueId(queueId);
        } catch (Exception ignore) {
        }
    }
}
