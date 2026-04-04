package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationActionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ActionType;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalReportsServiceImplBranchCoverage95Test {
    private static PortalReportsServiceImpl newService() {
        return new PortalReportsServiceImpl(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }


    @AfterEach
    void cleanup() {
        SecurityContextTestSupport.clear();
    }

    @Test
    void currentUserIdOrThrow_allBranches() {
        PortalReportsServiceImpl svc = newService();
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow"));

        Authentication notAuthed = mock(Authentication.class);
        when(notAuthed.isAuthenticated()).thenReturn(false);
        when(notAuthed.getPrincipal()).thenReturn("u@example.com");
        when(notAuthed.getName()).thenReturn("u@example.com");
        SecurityContextHolder.getContext().setAuthentication(notAuthed);
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow"));

        Authentication anon = new TestingAuthenticationToken("anonymousUser", "n/a");
        SecurityContextHolder.getContext().setAuthentication(anon);
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow"));

        AdministratorService administratorService = mock(AdministratorService.class);
        SecurityContextTestSupport.setAuthenticatedEmail("u@example.com");
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow"));

        UsersEntity me = new UsersEntity();
        me.setId(9L);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(me));
        Long id = ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow");
        assertEquals(9L, id);
    }

    @Test
    void normalizeReasonCode_and_text_branches() {
        assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "normalizeReasonCode", "   "));
        assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "normalizeReasonCode", new Object[]{null}));

        String code = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "normalizeReasonCode", "a".repeat(100));
        assertEquals(64, code.length());
        assertEquals(code, code.toUpperCase(Locale.ROOT));

        String t0 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "normalizeReasonText", new Object[]{null});
        assertNull(t0);
        String t1 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "normalizeReasonText", "   ");
        assertNull(t1);
        String t2 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "normalizeReasonText", " " + "a".repeat(300) + " ");
        assertEquals(255, t2.length());
    }

    @Test
    void helpers_asIntOrDefault_asDoubleOrDefault_castToStringKeyMap_branches() {
        Integer i0 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "asIntOrDefault", "123", 7);
        assertEquals(123, i0);
        Integer i1 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "asIntOrDefault", "abc", 7);
        assertEquals(7, i1);

        Double d0 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "asDoubleOrDefault", "0.5", 7.0);
        assertEquals(0.5, d0, 1e-9);
        Double d1 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "asDoubleOrDefault", "abc", 7.0);
        assertEquals(7.0, d1, 1e-9);

        Map<String, Object> m0 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "castToStringKeyMap", "not-map");
        assertNull(m0);
        LinkedHashMap<Object, Object> raw = new LinkedHashMap<>();
        raw.put(null, 1);
        raw.put(2, 3);
        Map<String, Object> m1 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "castToStringKeyMap", raw);
        assertEquals(1, m1.size());
        assertEquals(3, m1.get("2"));
    }

    @Test
    void hitTrigger_branches() {
        Boolean h0 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "hitTrigger", new Object[]{null, 1L, 1L, 1L, 0.0});
        assertFalse(Boolean.TRUE.equals(h0));
        Boolean h1 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "hitTrigger", Map.of(), 1L, 1L, 1L, 0.0);
        assertFalse(Boolean.TRUE.equals(h1));

        Map<String, Object> level = new LinkedHashMap<>();
        level.put("total_reports_min", 0);
        level.put("unique_reporters_min", -1);
        level.put("velocity_min_per_window", 0);
        Boolean h2 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "hitTrigger", level, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 0.0);
        assertFalse(Boolean.TRUE.equals(h2));

        Map<String, Object> level2 = new LinkedHashMap<>();
        level2.put("total_reports_min", "1");
        Boolean h3 = ReflectionTestUtils.invokeMethod(PortalReportsServiceImpl.class, "hitTrigger", level2, 1L, 0L, 0L, Double.NaN);
        assertTrue(Boolean.TRUE.equals(h3));
    }

    @Test
    void snapshotProfileFields_and_tryWriteReportSnapshot_branches() {
        ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "moderationActionsRepository", moderationActionsRepository);

        Map<String, Object> empty = ReflectionTestUtils.invokeMethod(svc, "snapshotProfileFields", new Object[]{null});
        assertTrue(empty.isEmpty());

        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setUsername("alice");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("avatarUrl", "http://a");
        profile.put("bio", null);
        profile.put("location", "c");
        profile.put("website", null);
        u.setMetadata(Map.of("profile", profile));
        Map<String, Object> snap = ReflectionTestUtils.invokeMethod(svc, "snapshotProfileFields", u);
        assertEquals(1L, snap.get("user_id"));
        assertEquals("alice", snap.get("username"));
        assertEquals("http://a", snap.get("avatarUrl"));
        assertEquals("c", snap.get("location"));

        ReflectionTestUtils.invokeMethod(svc, "tryWriteReportSnapshot", new Object[]{null, null, null});
        verify(moderationActionsRepository, never()).save(any());

        ModerationQueueEntity q0 = new ModerationQueueEntity();
        q0.setId(1L);
        ReportsEntity rep0 = new ReportsEntity();
        rep0.setId(2L);
        rep0.setReporterId(3L);
        rep0.setTargetType(ReportTargetType.PROFILE);
        rep0.setTargetId(4L);
        rep0.setCreatedAt(LocalDateTime.of(2026, 3, 2, 10, 0));

        when(moderationActionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.invokeMethod(svc, "tryWriteReportSnapshot", q0, rep0, null);

        ArgumentCaptor<ModerationActionsEntity> cap = ArgumentCaptor.forClass(ModerationActionsEntity.class);
        verify(moderationActionsRepository).save(cap.capture());
        ModerationActionsEntity a = cap.getValue();
        assertEquals(1L, a.getQueueId());
        assertEquals(3L, a.getActorUserId());
        assertEquals(ActionType.NOTE, a.getAction());
        assertEquals("REPORT_SNAPSHOT", a.getReason());
        assertTrue(String.valueOf(a.getSnapshot().get("content_snapshot_id")).contains(":at:"));
        assertFalse(a.getSnapshot().containsKey("target_snapshot"));

        ModerationActionsRepository moderationActionsRepository2 = mock(ModerationActionsRepository.class);
        ReflectionTestUtils.setField(svc, "moderationActionsRepository", moderationActionsRepository2);
        when(moderationActionsRepository2.save(any())).thenThrow(new RuntimeException("boom"));
        ReflectionTestUtils.invokeMethod(svc, "tryWriteReportSnapshot", q0, rep0, new LinkedHashMap<>());

        ReportsEntity rep1 = new ReportsEntity();
        rep1.setId(2L);
        rep1.setReporterId(3L);
        rep1.setTargetType(null);
        rep1.setTargetId(4L);
        rep1.setCreatedAt(null);
        ModerationActionsRepository moderationActionsRepository3 = mock(ModerationActionsRepository.class);
        ReflectionTestUtils.setField(svc, "moderationActionsRepository", moderationActionsRepository3);
        when(moderationActionsRepository3.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.invokeMethod(svc, "tryWriteReportSnapshot", q0, rep1, Map.of("k", "v"));
        ArgumentCaptor<ModerationActionsEntity> cap2 = ArgumentCaptor.forClass(ModerationActionsEntity.class);
        verify(moderationActionsRepository3).save(cap2.capture());
        assertEquals("report:2", cap2.getValue().getSnapshot().get("content_snapshot_id"));
        assertNull(cap2.getValue().getSnapshot().get("target_type"));
        assertEquals(Map.of("k", "v"), cap2.getValue().getSnapshot().get("target_snapshot"));
    }

    @Test
    void buildReporterTrustAgg_branches() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);

        double v0 = ReflectionTestUtils.invokeMethod(svc, "buildReporterTrustAgg", new Object[]{null});
        double v1 = ReflectionTestUtils.invokeMethod(svc, "buildReporterTrustAgg", List.of());
        assertTrue(Double.isNaN(v0));
        assertTrue(Double.isNaN(v1));

        ReportsEntity ignored = new ReportsEntity();
        double v2 = ReflectionTestUtils.invokeMethod(svc, "buildReporterTrustAgg", Arrays.asList(null, ignored));
        assertTrue(Double.isNaN(v2));

        ReportsEntity r1 = new ReportsEntity();
        r1.setReporterId(1L);
        ReportsEntity r2 = new ReportsEntity();
        r2.setReporterId(2L);
        ReportsEntity r3 = new ReportsEntity();
        r3.setReporterId(1L);

        UsersEntity u1 = new UsersEntity();
        u1.setId(1L);
        u1.setMetadata(Map.of("trust_score", 2.0));
        when(usersRepository.findById(1L)).thenReturn(Optional.of(u1));

        UsersEntity u2 = new UsersEntity();
        u2.setId(2L);
        u2.setMetadata(Map.of("trust_score", "0.9"));
        when(usersRepository.findById(2L)).thenReturn(Optional.of(u2));

        double v3 = ReflectionTestUtils.invokeMethod(svc, "buildReporterTrustAgg", List.of(r1, r2, r3));
        assertEquals((1.0 + 0.5 + 1.0) / 3.0, v3, 1e-9);
        verify(usersRepository, times(1)).findById(eq(1L));
    }

    @Test
    void sealRunningPipelineRun_branches() {
        ModerationPipelineRunRepository repo = mock(ModerationPipelineRunRepository.class);
        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", repo);

        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", new Object[]{null});
        verify(repo, never()).findFirstByQueueIdOrderByCreatedAtDesc(anyLong());

        when(repo.findFirstByQueueIdOrderByCreatedAtDesc(1L)).thenThrow(new RuntimeException("boom"));
        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", 1L);

        when(repo.findFirstByQueueIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.empty());
        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", 2L);

        ModerationPipelineRunEntity done = new ModerationPipelineRunEntity();
        done.setStatus(ModerationPipelineRunEntity.RunStatus.SUCCESS);
        when(repo.findFirstByQueueIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(done));
        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", 3L);
        verify(repo, never()).save(any());

        ModerationPipelineRunEntity running = new ModerationPipelineRunEntity();
        running.setStatus(ModerationPipelineRunEntity.RunStatus.RUNNING);
        running.setStartedAt(LocalDateTime.now().minusSeconds(1));
        when(repo.findFirstByQueueIdOrderByCreatedAtDesc(4L)).thenReturn(Optional.of(running));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", 4L);
        assertEquals(ModerationPipelineRunEntity.RunStatus.FAIL, running.getStatus());
        assertEquals(ModerationPipelineRunEntity.FinalDecision.HUMAN, running.getFinalDecision());
        assertNotNull(running.getEndedAt());
        assertNotNull(running.getTotalMs());

        ModerationPipelineRunEntity running2 = new ModerationPipelineRunEntity();
        running2.setStatus(ModerationPipelineRunEntity.RunStatus.RUNNING);
        when(repo.findFirstByQueueIdOrderByCreatedAtDesc(5L)).thenReturn(Optional.of(running2));
        when(repo.save(any())).thenThrow(new RuntimeException("boom"));
        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", 5L);
    }

    @Test
    void reportEntryMethods_coverMainBranches() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        ModerationRuleAutoRunner moderationRuleAutoRunner = mock(ModerationRuleAutoRunner.class);
        ModerationVecAutoRunner moderationVecAutoRunner = mock(ModerationVecAutoRunner.class);
        ModerationLlmAutoRunner moderationLlmAutoRunner = mock(ModerationLlmAutoRunner.class);

        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "commentsRepository", commentsRepository);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "moderationActionsRepository", moderationActionsRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", moderationRuleAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", moderationVecAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", moderationLlmAutoRunner);

        assertThrows(IllegalArgumentException.class, () -> svc.reportPost(null, "SPAM", "x"));
        assertThrows(IllegalArgumentException.class, () -> svc.reportComment(null, "SPAM", "x"));
        assertThrows(IllegalArgumentException.class, () -> svc.reportProfile(null, "SPAM", "x"));

        PostsEntity p = new PostsEntity();
        p.setId(9L);
        p.setIsDeleted(true);
        when(postsRepository.findById(9L)).thenReturn(Optional.of(p));
        assertThrows(IllegalArgumentException.class, () -> svc.reportPost(9L, "SPAM", "x"));

        CommentsEntity c = new CommentsEntity();
        c.setId(7L);
        c.setIsDeleted(true);
        when(commentsRepository.findById(7L)).thenReturn(Optional.of(c));
        assertThrows(IllegalArgumentException.class, () -> svc.reportComment(7L, "SPAM", "x"));

        UsersEntity u = new UsersEntity();
        u.setId(5L);
        u.setIsDeleted(true);
        when(usersRepository.findById(5L)).thenReturn(Optional.of(u));
        assertThrows(IllegalArgumentException.class, () -> svc.reportProfile(5L, "SPAM", "x"));

        String email = "u@example.com";
        SecurityContextTestSupport.setAuthenticatedEmail(email);
        UsersEntity me = new UsersEntity();
        me.setId(100L);
        when(administratorService.findByUsername(email)).thenReturn(Optional.of(me));

        PostsEntity p2 = new PostsEntity();
        p2.setId(9L);
        p2.setIsDeleted(false);
        when(postsRepository.findById(9L)).thenReturn(Optional.of(p2));

        when(reportsRepository.save(any())).thenAnswer(inv -> {
            ReportsEntity rep = inv.getArgument(0);
            rep.setId(300L);
            rep.setCreatedAt(null);
            return rep;
        });

        when(policyConfigRepository.findByContentType(any())).thenReturn(Optional.empty());
        when(fallbackConfigRepository.findAll()).thenReturn(List.of());
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());

        when(reportsRepository.countByTargetTypeAndTargetId(any(), any())).thenReturn(1L);

        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.REPORT, ContentType.POST, 9L))
                .thenReturn(Optional.empty());
        when(moderationQueueRepository.save(any())).thenAnswer(inv -> {
            ModerationQueueEntity q = inv.getArgument(0);
            q.setId(200L);
            return q;
        });
        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(200L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(1);

        PortalReportsService.ReportSubmitResult out = svc.reportPost(9L, "a".repeat(100), "  hello  ");
        assertEquals(300L, out.getReportId());
        assertEquals(200L, out.getQueueId());

        ArgumentCaptor<ReportsEntity> repCap = ArgumentCaptor.forClass(ReportsEntity.class);
        verify(reportsRepository).save(repCap.capture());
        assertEquals(ReportStatus.PENDING, repCap.getValue().getStatus());
        assertEquals(64, repCap.getValue().getReasonCode().length());
        assertEquals(repCap.getValue().getReasonCode(), repCap.getValue().getReasonCode().toUpperCase(Locale.ROOT));
        assertEquals("hello", repCap.getValue().getReasonText());

        ModerationQueueEntity existing = new ModerationQueueEntity();
        existing.setId(11L);
        existing.setCaseType(ModerationCaseType.REPORT);
        existing.setContentType(ContentType.COMMENT);
        existing.setContentId(7L);
        existing.setStatus(QueueStatus.PENDING);
        existing.setCurrentStage(QueueStage.RULE);
        CommentsEntity c2 = new CommentsEntity();
        c2.setId(7L);
        c2.setIsDeleted(false);
        when(commentsRepository.findById(7L)).thenReturn(Optional.of(c2));
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.REPORT, ContentType.COMMENT, 7L))
                .thenReturn(Optional.of(existing));
        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(11L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(1);
        PortalReportsService.ReportSubmitResult out2 = svc.reportComment(7L, "SPAM", "   ");
        assertEquals(11L, out2.getQueueId());

        UsersEntity profile = new UsersEntity();
        profile.setId(500L);
        profile.setIsDeleted(false);
        profile.setUsername("alice");
        profile.setMetadata(Map.of("profile", Map.of("bio", "b")));
        when(usersRepository.findById(500L)).thenReturn(Optional.of(profile));
        ModerationQueueEntity q3 = new ModerationQueueEntity();
        q3.setId(2L);
        q3.setCaseType(ModerationCaseType.REPORT);
        q3.setContentType(ContentType.PROFILE);
        q3.setContentId(500L);
        q3.setStatus(QueueStatus.PENDING);
        q3.setCurrentStage(QueueStage.RULE);
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.REPORT, ContentType.PROFILE, 500L))
                .thenReturn(Optional.of(q3));
        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(2L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(1);
        when(moderationActionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        PortalReportsService.ReportSubmitResult out3 = svc.reportProfile(500L, "spam", "x");
        assertEquals(2L, out3.getQueueId());
        verify(moderationActionsRepository, times(1)).save(any());
    }

    @Test
    void applyReportQueueRouting_branches() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);
        ModerationRuleAutoRunner moderationRuleAutoRunner = mock(ModerationRuleAutoRunner.class);
        ModerationVecAutoRunner moderationVecAutoRunner = mock(ModerationVecAutoRunner.class);
        ModerationLlmAutoRunner moderationLlmAutoRunner = mock(ModerationLlmAutoRunner.class);

        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", moderationRuleAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", moderationVecAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", moderationLlmAutoRunner);

        ReportsEntity rep = new ReportsEntity();
        rep.setId(1L);
        rep.setReporterId(55L);
        rep.setTargetType(ReportTargetType.POST);
        rep.setTargetId(99L);
        rep.setCreatedAt(LocalDateTime.now());

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(2L);
        q.setContentType(ContentType.POST);
        q.setContentId(99L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);

        when(reportsRepository.countByTargetTypeAndTargetIdAndCreatedAtAfter(eq(rep.getTargetType()), eq(rep.getTargetId()), any()))
                .thenReturn(1L);
        when(reportsRepository.countDistinctReporterIdByTargetTypeAndTargetIdAndCreatedAtAfter(eq(rep.getTargetType()), eq(rep.getTargetId()), any()))
                .thenReturn(1L);
        when(reportsRepository.findAllByTargetTypeAndTargetIdAndCreatedAtAfter(eq(rep.getTargetType()), eq(rep.getTargetId()), any()))
                .thenReturn(null);

        UsersEntity u = new UsersEntity();
        u.setId(55L);
        u.setMetadata(Map.of("trust_score", 0.9));
        when(usersRepository.findById(55L)).thenReturn(Optional.of(u));

        Map<String, Object> urgent = new LinkedHashMap<>();
        urgent.put("total_reports_min", 100);
        Map<String, Object> standard = new LinkedHashMap<>();
        standard.put("total_reports_min", 1);
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("window_minutes", 0);
        trigger.put("urgent", urgent);
        trigger.put("standard", standard);
        trigger.put("light", Map.of());
        ModerationPolicyConfigEntity policy = new ModerationPolicyConfigEntity();
        policy.setContentType(ContentType.POST);
        policy.setConfig(Map.of("review_trigger", trigger));
        when(policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy));

        when(moderationQueueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q);
        assertEquals(QueueStatus.HUMAN, q.getStatus());
        assertEquals(QueueStage.HUMAN, q.getCurrentStage());

        ModerationPolicyConfigEntity policy2 = new ModerationPolicyConfigEntity();
        policy2.setContentType(ContentType.POST);
        policy2.setConfig(Map.of("review_trigger", "not-a-map"));
        when(policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy2));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setUpdatedAt(LocalDateTime.now());
        fb.setReportHumanThreshold(0);
        when(fallbackConfigRepository.findAll()).thenReturn(List.of(fb));
        when(reportsRepository.countByTargetTypeAndTargetId(eq(rep.getTargetType()), eq(rep.getTargetId()))).thenReturn(0L);
        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(2L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(1);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);
        ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q);

        when(policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.empty());
        when(fallbackConfigRepository.findAll()).thenThrow(new RuntimeException("ignore"));
        when(reportsRepository.countByTargetTypeAndTargetId(eq(rep.getTargetType()), eq(rep.getTargetId()))).thenReturn(5L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);
        ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q);

        verify(moderationRuleAutoRunner, atLeastOnce()).runOnce();
        verify(moderationVecAutoRunner, atLeastOnce()).runOnce();
        verify(moderationLlmAutoRunner, atLeastOnce()).runOnce();
    }
}

