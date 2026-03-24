package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.service.AccountSecurityService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationAutoKickService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.notify.AccountSecurityNotificationMailer;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountProfileControllerAfterCommitTest {

    @AfterEach
    void clearSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void scheduleModerationAutoRunAfterCommit_shouldNoop_whenQueueIdNull() throws Exception {
        ModerationAutoKickService moderationAutoKickService = mock(ModerationAutoKickService.class);
        AccountProfileController c = controller(moderationAutoKickService);

        invokeSchedule(c, null);

        verify(moderationAutoKickService, times(0)).kickQueueId(Mockito.any());
    }

    @Test
    void scheduleModerationAutoRunAfterCommit_shouldKickImmediately_whenNoTransactionSync() throws Exception {
        ModerationAutoKickService moderationAutoKickService = mock(ModerationAutoKickService.class);
        AccountProfileController c = controller(moderationAutoKickService);

        invokeSchedule(c, 7L);

        verify(moderationAutoKickService).kickQueueId(7L);
    }

    @Test
    void scheduleModerationAutoRunAfterCommit_shouldRegisterSynchronization_andRunAfterCommit() throws Exception {
        ModerationAutoKickService moderationAutoKickService = mock(ModerationAutoKickService.class);
        doThrow(new RuntimeException("boom")).when(moderationAutoKickService).kickQueueId(7L);
        AccountProfileController c = controller(moderationAutoKickService);

        TransactionSynchronizationManager.initSynchronization();
        invokeSchedule(c, 7L);

        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);

        syncs.get(0).afterCommit();

        verify(moderationAutoKickService).kickQueueId(7L);
    }

    private static AccountProfileController controller(ModerationAutoKickService moderationAutoKickService) {
        return new AccountProfileController(
                mock(UsersRepository.class),
                mock(AccountSecurityService.class),
                mock(AccountTotpService.class),
                mock(EmailVerificationService.class),
                mock(EmailVerificationMailer.class),
                mock(Security2faPolicyService.class),
                mock(NotificationsService.class),
                mock(AccountSecurityNotificationMailer.class),
                mock(AuditLogWriter.class),
                mock(AuditDiffBuilder.class),
                mock(ModerationQueueRepository.class),
                mock(ModerationActionsRepository.class),
                moderationAutoKickService,
                mock(com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner.class)
        );
    }

    private static void invokeSchedule(AccountProfileController c, Long queueId) throws Exception {
        Method m = AccountProfileController.class.getDeclaredMethod("scheduleModerationAutoRunAfterCommit", Long.class);
        m.setAccessible(true);
        m.invoke(c, queueId);
    }
}
