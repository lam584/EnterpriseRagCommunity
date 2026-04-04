package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.PortalReportsService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationAutoKickService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalReportsServiceImplAfterCommitTest {

    private static PortalReportsServiceImpl newService() {
        return new PortalReportsServiceImpl(
                mock(com.example.EnterpriseRagCommunity.repository.content.ReportsRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.content.PostsRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.content.CommentsRepository.class),
                mock(com.example.EnterpriseRagCommunity.service.AdministratorService.class),
                mock(com.example.EnterpriseRagCommunity.repository.access.UsersRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository.class),
                mock(com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner.class),
                mock(com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner.class),
                mock(com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner.class)
        );
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void reportComment_shouldKickAfterCommit_whenTransactionSynchronizationActive() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModerationAutoKickService moderationAutoKickService = mock(ModerationAutoKickService.class);

        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "commentsRepository", commentsRepository);
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", mock(ModerationPipelineRunRepository.class));
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", mock(ModerationConfidenceFallbackConfigRepository.class));
        ReflectionTestUtils.setField(svc, "policyConfigRepository", mock(ModerationPolicyConfigRepository.class));
        ReflectionTestUtils.setField(svc, "moderationAutoKickService", moderationAutoKickService);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        CommentsEntity comment = new CommentsEntity();
        comment.setId(8L);
        comment.setIsDeleted(false);
        when(commentsRepository.findById(8L)).thenReturn(Optional.of(comment));

        when(reportsRepository.save(any())).thenAnswer(inv -> {
            ReportsEntity rep = inv.getArgument(0);
            rep.setId(300L);
            rep.setStatus(ReportStatus.PENDING);
            return rep;
        });
        when(reportsRepository.countByTargetTypeAndTargetId(any(), any())).thenReturn(1L);

        ModerationQueueEntity queue = new ModerationQueueEntity();
        queue.setId(901L);
        queue.setCaseType(ModerationCaseType.REPORT);
        queue.setContentType(ContentType.COMMENT);
        queue.setContentId(8L);
        queue.setStatus(QueueStatus.PENDING);
        queue.setCurrentStage(QueueStage.RULE);
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.REPORT, ContentType.COMMENT, 8L))
                .thenReturn(Optional.of(queue));
        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(901L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();

        PortalReportsService.ReportSubmitResult out = svc.reportComment(8L, "SPAM", "x");

        assertEquals(901L, out.getQueueId());
        verify(moderationAutoKickService, never()).kickQueueId(901L);

        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, syncs.size());
        syncs.get(0).afterCommit();

        verify(moderationAutoKickService).kickQueueId(901L);
    }
}
