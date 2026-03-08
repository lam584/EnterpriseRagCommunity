package com.example.EnterpriseRagCommunity.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AiPostSummaryTriggerServiceTest {

    @Test
    void scheduleGenerateAfterCommit_should_do_nothing_when_post_id_is_null() {
        AiPostSummaryService aiPostSummaryService = mock(AiPostSummaryService.class);
        AiPostSummaryTriggerService triggerService = new AiPostSummaryTriggerService(aiPostSummaryService);

        triggerService.scheduleGenerateAfterCommit(null, 3L);
        verifyNoInteractions(aiPostSummaryService);
    }

    @Test
    void scheduleGenerateAfterCommit_should_defer_until_after_commit_when_sync_active() {
        AiPostSummaryService aiPostSummaryService = mock(AiPostSummaryService.class);
        AiPostSummaryTriggerService triggerService = new AiPostSummaryTriggerService(aiPostSummaryService);

        TransactionSynchronizationManager.initSynchronization();
        try {
            triggerService.scheduleGenerateAfterCommit(10L, 3L);
            verifyNoInteractions(aiPostSummaryService);

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(aiPostSummaryService).generateForPostIdAsync(10L, 3L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void scheduleGenerateAfterCommit_should_call_immediately_when_sync_inactive() {
        AiPostSummaryService aiPostSummaryService = mock(AiPostSummaryService.class);
        AiPostSummaryTriggerService triggerService = new AiPostSummaryTriggerService(aiPostSummaryService);

        triggerService.scheduleGenerateAfterCommit(10L, 3L);
        verify(aiPostSummaryService).generateForPostIdAsync(10L, 3L);
    }
}
