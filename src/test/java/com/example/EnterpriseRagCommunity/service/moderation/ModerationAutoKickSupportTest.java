package com.example.EnterpriseRagCommunity.service.moderation;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ModerationAutoKickSupportTest {

    @Test
    void kickQueueIdAfterCommit_should_run_immediately_without_transaction() {
        ModerationAutoKickService service = mock(ModerationAutoKickService.class);

        ModerationAutoKickSupport.kickQueueIdAfterCommit(service, 7L);

        verify(service, times(1)).kickQueueId(7L);
    }

    @Test
    void kickQueueIdAfterCommit_should_register_after_commit_when_transaction_active() {
        ModerationAutoKickService service = mock(ModerationAutoKickService.class);
        TransactionSynchronizationManager.initSynchronization();
        try {
            ModerationAutoKickSupport.kickQueueIdAfterCommit(service, 9L);

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(service, times(1)).kickQueueId(9L);
    }
}
