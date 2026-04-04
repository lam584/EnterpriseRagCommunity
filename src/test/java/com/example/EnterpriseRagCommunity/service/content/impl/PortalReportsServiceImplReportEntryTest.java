package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationActionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.PortalReportsService;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner;
import com.example.EnterpriseRagCommunity.testsupport.SecurityContextTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalReportsServiceImplReportEntryTest {
    private static PortalReportsServiceImpl newService() {
        return new PortalReportsServiceImpl(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }


    @AfterEach
    void cleanup() {
        SecurityContextTestSupport.clear();
    }

    @Test
    void reportPost_postIdNull_throws() {
        PortalReportsServiceImpl svc = newService();
        assertThrows(IllegalArgumentException.class, () -> svc.reportPost(null, "SPAM", "x"));
    }

    @Test
    void reportPost_postMissing_throws() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);

        when(postsRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> svc.reportPost(9L, "SPAM", "x"));
    }

    @Test
    void reportPost_postDeleted_throws() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);

        PostsEntity p = new PostsEntity();
        p.setId(9L);
        p.setIsDeleted(true);
        when(postsRepository.findById(9L)).thenReturn(Optional.of(p));

        assertThrows(IllegalArgumentException.class, () -> svc.reportPost(9L, "SPAM", "x"));
    }

    @Test
    void reportPost_authMissing_throwsAuthenticationException() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);

        PostsEntity p = new PostsEntity();
        p.setId(9L);
        p.setIsDeleted(false);
        when(postsRepository.findById(9L)).thenReturn(Optional.of(p));

        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> svc.reportPost(9L, "SPAM", "x"));
    }

    @Test
    void reportPost_success_savesReportAndEnqueuesAndReturnsIds() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        ModerationRuleAutoRunner moderationRuleAutoRunner = mock(ModerationRuleAutoRunner.class);
        ModerationVecAutoRunner moderationVecAutoRunner = mock(ModerationVecAutoRunner.class);
        ModerationLlmAutoRunner moderationLlmAutoRunner = mock(ModerationLlmAutoRunner.class);

        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", moderationRuleAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", moderationVecAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", moderationLlmAutoRunner);

        String email = "u@example.com";
        SecurityContextTestSupport.setAuthenticatedEmail(email);
        UsersEntity me = new UsersEntity();
        me.setId(100L);
        when(administratorService.findByUsername(email)).thenReturn(Optional.of(me));

        PostsEntity p = new PostsEntity();
        p.setId(9L);
        p.setIsDeleted(false);
        when(postsRepository.findById(9L)).thenReturn(Optional.of(p));

        when(reportsRepository.save(any())).thenAnswer(inv -> {
            ReportsEntity rep = inv.getArgument(0);
            rep.setId(300L);
            return rep;
        });

        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.REPORT, ContentType.POST, 9L))
                .thenReturn(Optional.empty());
        when(moderationQueueRepository.save(any())).thenAnswer(inv -> {
            ModerationQueueEntity q = inv.getArgument(0);
            q.setId(200L);
            return q;
        });

        when(policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.empty());
        when(fallbackConfigRepository.findAll()).thenReturn(List.of());
        when(reportsRepository.countByTargetTypeAndTargetId(eq(ReportTargetType.POST), eq(9L))).thenReturn(1L);
        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(200L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(1);
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());

        String longCode = "a".repeat(100);
        PortalReportsService.ReportSubmitResult out = svc.reportPost(9L, longCode, "  hello  ");

        assertEquals(300L, out.getReportId());
        assertEquals(200L, out.getQueueId());

        ArgumentCaptor<ReportsEntity> repCap = ArgumentCaptor.forClass(ReportsEntity.class);
        verify(reportsRepository).save(repCap.capture());
        ReportsEntity saved = repCap.getValue();
        assertEquals(100L, saved.getReporterId());
        assertEquals(ReportTargetType.POST, saved.getTargetType());
        assertEquals(9L, saved.getTargetId());
        assertEquals(64, saved.getReasonCode().length());
        assertEquals(saved.getReasonCode(), saved.getReasonCode().toUpperCase(Locale.ROOT));
        assertEquals("hello", saved.getReasonText());

        verify(moderationRuleAutoRunner).runOnce();
        verify(moderationVecAutoRunner).runOnce();
        verify(moderationLlmAutoRunner).runOnce();
    }

    @Test
    void reportComment_success_reasonTextBlank_persistsNull_andReusesExistingQueue() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);

        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "commentsRepository", commentsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", mock(ModerationRuleAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", mock(ModerationVecAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", mock(ModerationLlmAutoRunner.class));

        String email = "u@example.com";
        SecurityContextTestSupport.setAuthenticatedEmail(email);
        UsersEntity me = new UsersEntity();
        me.setId(100L);
        when(administratorService.findByUsername(email)).thenReturn(Optional.of(me));

        CommentsEntity c = new CommentsEntity();
        c.setId(7L);
        c.setIsDeleted(false);
        when(commentsRepository.findById(7L)).thenReturn(Optional.of(c));

        ModerationQueueEntity existing = new ModerationQueueEntity();
        existing.setId(11L);
        existing.setCaseType(ModerationCaseType.REPORT);
        existing.setContentType(ContentType.COMMENT);
        existing.setContentId(7L);
        existing.setStatus(QueueStatus.PENDING);
        existing.setCurrentStage(QueueStage.RULE);
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.REPORT, ContentType.COMMENT, 7L))
                .thenReturn(Optional.of(existing));

        when(reportsRepository.save(any())).thenAnswer(inv -> {
            ReportsEntity rep = inv.getArgument(0);
            rep.setId(22L);
            return rep;
        });

        when(policyConfigRepository.findByContentType(ContentType.COMMENT)).thenReturn(Optional.empty());
        when(fallbackConfigRepository.findAll()).thenReturn(List.of());
        when(reportsRepository.countByTargetTypeAndTargetId(eq(ReportTargetType.COMMENT), eq(7L))).thenReturn(1L);
        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(11L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(1);
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(11L)).thenReturn(Optional.empty());

        PortalReportsService.ReportSubmitResult out = svc.reportComment(7L, "SPAM", "   ");

        assertEquals(22L, out.getReportId());
        assertEquals(11L, out.getQueueId());

        ArgumentCaptor<ReportsEntity> repCap = ArgumentCaptor.forClass(ReportsEntity.class);
        verify(reportsRepository).save(repCap.capture());
        assertNull(repCap.getValue().getReasonText());
    }

    @Test
    void reportProfile_userIdNull_throws() {
        PortalReportsServiceImpl svc = newService();
        assertThrows(IllegalArgumentException.class, () -> svc.reportProfile(null, "SPAM", "x"));
    }

    @Test
    void reportProfile_userMissing_throws() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);

        when(usersRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> svc.reportProfile(9L, "SPAM", "x"));
    }

    @Test
    void reportProfile_userDeleted_throws() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);

        UsersEntity u = new UsersEntity();
        u.setId(9L);
        u.setIsDeleted(true);
        when(usersRepository.findById(9L)).thenReturn(Optional.of(u));
        assertThrows(IllegalArgumentException.class, () -> svc.reportProfile(9L, "SPAM", "x"));
    }

    @Test
    void reportProfile_success_writesSnapshotAction() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);

        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "moderationActionsRepository", moderationActionsRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", mock(ModerationRuleAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", mock(ModerationVecAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", mock(ModerationLlmAutoRunner.class));

        String email = "u@example.com";
        SecurityContextTestSupport.setAuthenticatedEmail(email);
        UsersEntity me = new UsersEntity();
        me.setId(100L);
        when(administratorService.findByUsername(email)).thenReturn(Optional.of(me));

        UsersEntity u = new UsersEntity();
        u.setId(500L);
        u.setIsDeleted(false);
        u.setUsername("alice");
        u.setMetadata(Map.of("profile", Map.of(
                "avatarUrl", "http://a",
                "bio", "b",
                "location", "c",
                "website", "http://w"
        )));
        when(usersRepository.findById(500L)).thenReturn(Optional.of(u));

        when(reportsRepository.save(any())).thenAnswer(inv -> {
            ReportsEntity rep = inv.getArgument(0);
            rep.setId(301L);
            rep.setCreatedAt(null);
            return rep;
        });

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(201L);
        q.setCaseType(ModerationCaseType.REPORT);
        q.setContentType(ContentType.PROFILE);
        q.setContentId(500L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.REPORT, ContentType.PROFILE, 500L))
                .thenReturn(Optional.of(q));

        when(moderationActionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(policyConfigRepository.findByContentType(ContentType.PROFILE)).thenReturn(Optional.empty());
        when(fallbackConfigRepository.findAll()).thenReturn(List.of());
        when(reportsRepository.countByTargetTypeAndTargetId(eq(ReportTargetType.PROFILE), eq(500L))).thenReturn(1L);
        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(201L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(1);
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(201L)).thenReturn(Optional.of(new ModerationPipelineRunEntity()));

        PortalReportsService.ReportSubmitResult out = svc.reportProfile(500L, "spam", "x");

        assertEquals(301L, out.getReportId());
        assertEquals(201L, out.getQueueId());

        ArgumentCaptor<ModerationActionsEntity> cap = ArgumentCaptor.forClass(ModerationActionsEntity.class);
        verify(moderationActionsRepository).save(cap.capture());
        ModerationActionsEntity a = cap.getValue();
        assertEquals(201L, a.getQueueId());
        assertEquals(100L, a.getActorUserId());
        assertEquals("REPORT_SNAPSHOT", a.getReason());
        assertNotNull(a.getSnapshot());
        assertEquals("report:301", a.getSnapshot().get("content_snapshot_id"));
    }

    @Test
    void reportProfile_snapshotSaveThrows_isSwallowed() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);

        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "moderationActionsRepository", moderationActionsRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", mock(ModerationRuleAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", mock(ModerationVecAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", mock(ModerationLlmAutoRunner.class));

        String email = "u@example.com";
        SecurityContextTestSupport.setAuthenticatedEmail(email);
        UsersEntity me = new UsersEntity();
        me.setId(100L);
        when(administratorService.findByUsername(email)).thenReturn(Optional.of(me));

        UsersEntity u = new UsersEntity();
        u.setId(500L);
        u.setIsDeleted(false);
        u.setUsername("alice");
        u.setMetadata(Map.of("profile", Map.of("bio", "b")));
        when(usersRepository.findById(500L)).thenReturn(Optional.of(u));

        when(reportsRepository.save(any())).thenAnswer(inv -> {
            ReportsEntity rep = inv.getArgument(0);
            rep.setId(1L);
            return rep;
        });

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(2L);
        q.setCaseType(ModerationCaseType.REPORT);
        q.setContentType(ContentType.PROFILE);
        q.setContentId(500L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.REPORT, ContentType.PROFILE, 500L))
                .thenReturn(Optional.of(q));

        when(moderationActionsRepository.save(any())).thenThrow(new RuntimeException("boom"));

        when(policyConfigRepository.findByContentType(ContentType.PROFILE)).thenReturn(Optional.empty());
        when(fallbackConfigRepository.findAll()).thenReturn(List.of());
        when(reportsRepository.countByTargetTypeAndTargetId(eq(ReportTargetType.PROFILE), eq(500L))).thenReturn(1L);
        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(2L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(1);
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.empty());

        PortalReportsService.ReportSubmitResult out = svc.reportProfile(500L, "spam", "x");

        assertEquals(1L, out.getReportId());
        assertEquals(2L, out.getQueueId());
        verify(moderationActionsRepository).save(any());
        verify(moderationQueueRepository, never()).save(any());
    }
}

