package com.example.EnterpriseRagCommunity.service.moderation.impl;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueItemDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationQueueQueryDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationActionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ActionType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Source;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.UsersService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationAutoKickService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import com.example.EnterpriseRagCommunity.service.moderation.RiskLabelingService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentIndexVisibilitySyncService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexVisibilitySyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AdminModerationQueueServiceImplBranchCoverageTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void helperMethods_shouldCoverStaticAndValidationBranches() throws Exception {
        assertEquals(ReportTargetType.POST, invokeStatic("toReportTargetType", new Class[]{ContentType.class}, ContentType.POST));
        assertEquals(ReportTargetType.COMMENT, invokeStatic("toReportTargetType", new Class[]{ContentType.class}, ContentType.COMMENT));
        assertEquals(ReportTargetType.PROFILE, invokeStatic("toReportTargetType", new Class[]{ContentType.class}, ContentType.PROFILE));
        assertEquals(ReportTargetType.POST, invokeStatic("toReportTargetType", new Class[]{ContentType.class}, (Object) null));

        assertEquals("帖子", invokeStatic("labelFor", new Class[]{ContentType.class}, ContentType.POST));
        assertEquals("评论", invokeStatic("labelFor", new Class[]{ContentType.class}, ContentType.COMMENT));
        assertEquals("资料", invokeStatic("labelFor", new Class[]{ContentType.class}, ContentType.PROFILE));
        assertEquals("内容", invokeStatic("labelFor", new Class[]{ContentType.class}, (Object) null));

        assertNull(invokeStatic("normalizeReviewStageInput", new Class[]{String.class}, (Object) null));
        assertNull(invokeStatic("normalizeReviewStageInput", new Class[]{String.class}, "   "));
        assertEquals("default", invokeStatic("normalizeReviewStageInput", new Class[]{String.class}, " default "));
        assertEquals("reported", invokeStatic("normalizeReviewStageInput", new Class[]{String.class}, "REPORTED"));
        assertEquals("appeal", invokeStatic("normalizeReviewStageInput", new Class[]{String.class}, "Appeal"));
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> invokeStatic("normalizeReviewStageInput", new Class[]{String.class}, "unknown"));

        assertNull(invokeStatic("strOrNull", new Class[]{Object.class}, (Object) null));
        assertNull(invokeStatic("strOrNull", new Class[]{Object.class}, " "));
        assertEquals("x", invokeStatic("strOrNull", new Class[]{Object.class}, "  x  "));

        assertNull(invokeStatic("snippet", new Class[]{String.class, int.class}, null, 5));
        assertNull(invokeStatic("snippet", new Class[]{String.class, int.class}, "   ", 5));
        assertEquals("abc", invokeStatic("snippet", new Class[]{String.class, int.class}, " abc ", 5));
        assertEquals("abc...", invokeStatic("snippet", new Class[]{String.class, int.class}, "abcdef", 3));

        assertNull(invokeStatic("excerpt", new Class[]{String.class, int.class}, null, 3));
        assertEquals("a b", invokeStatic("excerpt", new Class[]{String.class, int.class}, "a \n b", 8));
        assertEquals("abc...", invokeStatic("excerpt", new Class[]{String.class, int.class}, "abcdef", 3));
    }

    @Test
    void privateDataHelpers_shouldCoverReadMapReadProfileAndReportSnippet() throws Exception {
        UsersEntity u = new UsersEntity();
        u.setMetadata(Map.of("profile", Map.of("bio", "  hi  ", "avatarUrl", "")));
        assertEquals("hi", invokeStatic("readUserProfileString", new Class[]{UsersEntity.class, String.class}, u, "bio"));
        assertNull(invokeStatic("readUserProfileString", new Class[]{UsersEntity.class, String.class}, u, "avatarUrl"));
        assertNull(invokeStatic("readUserProfileString", new Class[]{UsersEntity.class, String.class}, u, "missing"));
        assertNull(invokeStatic("readUserProfileString", new Class[]{UsersEntity.class, String.class}, null, "bio"));

        Map<String, Object> in = new HashMap<>();
        in.put("k1", Map.of("a", 1, 2, "v"));
        Map<String, Object> out = invokeStatic("readMap", new Class[]{Map.class, String.class}, in, "k1");
        assertEquals(2, out.size());
        assertEquals(1, out.get("a"));
        assertEquals("v", out.get("2"));
        assertTrue(((Map<?, ?>) invokeStatic("readMap", new Class[]{Map.class, String.class}, null, "k")).isEmpty());
        assertTrue(((Map<?, ?>) invokeStatic("readMap", new Class[]{Map.class, String.class}, Map.of("x", "y"), "x")).isEmpty());

        ReportsEntity r1 = new ReportsEntity();
        assertEquals("举报原因：—", invokeStatic("buildReportReasonSnippet", new Class[]{ReportsEntity.class}, r1));
        ReportsEntity r2 = new ReportsEntity();
        r2.setReasonCode("abuse");
        r2.setReasonText("  spam text ");
        assertEquals("举报原因：abuse - spam text", invokeStatic("buildReportReasonSnippet", new Class[]{ReportsEntity.class}, r2));
        ReportsEntity r3 = new ReportsEntity();
        r3.setReasonText("x".repeat(200));
        String s3 = invokeStatic("buildReportReasonSnippet", new Class[]{ReportsEntity.class}, r3);
        assertTrue(s3.startsWith("举报原因："));
        assertTrue(s3.endsWith("..."));
    }

    @Test
    void profileContent_shouldFallbackToSnapshotWhenPendingMissing() throws Exception {
        Fixture f = new Fixture();
        ModerationActionsEntity old = new ModerationActionsEntity();
        old.setId(1L);
        old.setAction(ActionType.NOTE);
        old.setReason("PROFILE_PENDING_SNAPSHOT");
        old.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        old.setSnapshot(Map.of("pending_profile", Map.of("username", "oldU")));
        ModerationActionsEntity latest = new ModerationActionsEntity();
        latest.setId(2L);
        latest.setAction(ActionType.NOTE);
        latest.setReason("PROFILE_PENDING_SNAPSHOT");
        latest.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        latest.setSnapshot(Map.of("pending_profile", Map.of("username", "newU", "bio", "newB"), "pending_submitted_at", "2026-03-09T12:00:00"));
        when(f.moderationActionsRepository.findAllByQueueId(99L)).thenReturn(List.of(old, latest));

        UsersEntity u = new UsersEntity();
        u.setId(7L);
        u.setUsername("publicU");
        u.setMetadata(Map.of("profile", Map.of("bio", "publicB")));

        AdminModerationQueueDetailDTO.ProfileContent pc = invoke(f.service, "toProfileContent", new Class[]{UsersEntity.class, Long.class}, u, 99L);
        assertNotNull(pc);
        assertEquals("newU", pc.getPendingUsername());
        assertEquals("newB", pc.getPendingBio());
        assertEquals("2026-03-09T12:00:00", pc.getPendingSubmittedAt());
    }

    @Test
    void list_shouldCoverSummaryRiskTagsAndChunkProgress() {
        Fixture f = new Fixture();
        ModerationQueueEntity postQ = queue(1L, ModerationCaseType.CONTENT, ContentType.POST, 11L, QueueStatus.PENDING);
        ModerationQueueEntity commentQ = queue(2L, ModerationCaseType.REPORT, ContentType.COMMENT, 22L, QueueStatus.HUMAN);
        ModerationQueueEntity profileQ = queue(3L, ModerationCaseType.CONTENT, ContentType.PROFILE, 33L, QueueStatus.REVIEWING);
        when(f.moderationQueueRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(postQ, commentQ, profileQ)));

        PostsEntity post = new PostsEntity();
        post.setId(11L);
        post.setTitle("pt");
        post.setContent("pc");
        post.setAuthorId(101L);
        when(f.postsRepository.findAllById(Set.of(11L))).thenReturn(List.of(post));
        when(f.postsRepository.findAllById(Set.of(201L))).thenReturn(List.of());

        CommentsEntity c = new CommentsEntity();
        c.setId(22L);
        c.setPostId(201L);
        c.setContent("cc");
        c.setAuthorId(102L);
        when(f.commentsRepository.findAllById(Set.of(22L))).thenReturn(List.of(c));
        UsersEntity u = new UsersEntity();
        u.setId(33L);
        u.setUsername("u33");
        when(f.usersRepository.findAllById(Set.of(33L))).thenReturn(List.of(u));

        when(f.riskLabelingService.getRiskTagItemsByTargets(eq(ContentType.POST), any())).thenReturn(Map.of(11L, Arrays.asList(
                new RiskLabelingService.RiskTagItem("violence", "Violence"),
                new RiskLabelingService.RiskTagItem(" ", "Blank"),
                null
        )));
        when(f.riskLabelingService.getRiskTagItemsByTargets(eq(ContentType.COMMENT), any())).thenReturn(Map.of(22L, List.of(
                new RiskLabelingService.RiskTagItem("abuse", "Abuse")
        )));
        when(f.riskLabelingService.getRiskTagItemsByTargets(eq(ContentType.PROFILE), any())).thenReturn(Map.of());
        ModerationChunkReviewService.ProgressSummary ps = new ModerationChunkReviewService.ProgressSummary();
        ps.queueId = 2L;
        ps.status = "RUNNING";
        ps.total = 4;
        ps.completed = 2;
        ps.failed = 1;
        ps.updatedAt = LocalDateTime.now();
        when(f.moderationChunkReviewService.loadProgressSummaries(any())).thenReturn(Map.of(2L, ps));
        when(f.reportsRepository.findByTargetTypeAndTargetId(eq(ReportTargetType.COMMENT), eq(22L), any())).thenReturn(new PageImpl<>(List.of()));

        Page<AdminModerationQueueItemDTO> out = f.service.list(new ModerationQueueQueryDTO());
        assertEquals(3, out.getContent().size());
        assertEquals(List.of("violence"), out.getContent().get(0).getRiskTags());
        assertNotNull(out.getContent().get(1).getChunkProgress());
        assertEquals("u33", out.getContent().get(2).getSummary().getTitle());
    }

    @Test
    void getDetail_shouldCoverPostAndReportsAndAttachments() {
        Fixture f = new Fixture();
        ModerationQueueEntity q = queue(10L, ModerationCaseType.REPORT, ContentType.POST, 91L, QueueStatus.PENDING);
        when(f.moderationQueueRepository.findById(10L)).thenReturn(Optional.of(q));
        PostsEntity p = new PostsEntity();
        p.setId(91L);
        p.setAuthorId(7L);
        p.setTitle("t");
        p.setContent("content");
        p.setStatus(PostStatus.PENDING);
        p.setBoardId(8L);
        when(f.postsRepository.findById(91L)).thenReturn(Optional.of(p));

        PostAttachmentsEntity a = new PostAttachmentsEntity();
        a.setId(501L);
        a.setFileAssetId(7001L);
        when(f.postAttachmentsRepository.findByPostId(eq(91L), any())).thenReturn(new PageImpl<>(List.of(a)));
        when(f.fileAssetExtractionsRepository.findAllById(Set.of(7001L))).thenReturn(List.of());

        ReportsEntity r = new ReportsEntity();
        r.setId(301L);
        r.setReporterId(401L);
        r.setReasonCode("ab");
        r.setReasonText("txt");
        r.setStatus(ReportStatus.PENDING);
        when(f.reportsRepository.findByTargetTypeAndTargetId(eq(ReportTargetType.POST), eq(91L), any()))
                .thenReturn(new PageImpl<>(List.of(r)));
        when(f.riskLabelingService.getRiskTagItems(ContentType.POST, 91L)).thenReturn(List.of(new RiskLabelingService.RiskTagItem("tag1", "Tag 1")));
        when(f.moderationChunkReviewService.loadProgressSummaries(any())).thenReturn(Map.of());

        AdminModerationQueueDetailDTO out = f.service.getDetail(10L);
        assertNotNull(out.getPost());
        assertEquals(1, out.getReports().size());
        assertEquals(List.of("tag1"), out.getRiskTags());
    }

    @Test
    void approveAndReject_shouldCoverStatusGuardsAndContentPaths() {
        Fixture f = new Fixture();
        AdminModerationQueueServiceImpl svc = spy(f.service);
        lenient().doReturn(detail(1L, QueueStatus.APPROVED, QueueStage.HUMAN)).when(svc).getDetail(1L);

        ModerationQueueEntity approved = queue(1L, ModerationCaseType.CONTENT, ContentType.POST, 101L, QueueStatus.APPROVED);
        when(f.moderationQueueRepository.findById(1L)).thenReturn(Optional.of(approved));
        assertEquals(QueueStatus.APPROVED, svc.autoApprove(1L, "ok", "t1").getStatus());

        ModerationQueueEntity rejected = queue(2L, ModerationCaseType.CONTENT, ContentType.POST, 102L, QueueStatus.REJECTED);
        when(f.moderationQueueRepository.findById(2L)).thenReturn(Optional.of(rejected));
        assertThrows(IllegalStateException.class, () -> svc.autoApprove(2L, "ok", "t2"));

        ModerationQueueEntity pendComment = queue(3L, ModerationCaseType.CONTENT, ContentType.COMMENT, 103L, QueueStatus.PENDING);
        when(f.moderationQueueRepository.findById(3L)).thenReturn(Optional.of(pendComment));
        CommentsEntity c = new CommentsEntity();
        c.setId(103L);
        c.setStatus(CommentStatus.PENDING);
        c.setIsDeleted(false);
        when(f.commentsRepository.findById(103L)).thenReturn(Optional.of(c));
        lenient().doReturn(detail(3L, QueueStatus.APPROVED, QueueStage.LLM)).when(svc).getDetail(3L);
        assertEquals(QueueStatus.APPROVED, svc.autoApprove(3L, "ok", "t3").getStatus());

        ModerationQueueEntity pendProfile = queue(4L, ModerationCaseType.CONTENT, ContentType.PROFILE, 104L, QueueStatus.PENDING);
        when(f.moderationQueueRepository.findById(4L)).thenReturn(Optional.of(pendProfile));
        UsersEntity u = new UsersEntity();
        u.setId(104L);
        u.setIsDeleted(false);
        u.setMetadata(new HashMap<>(Map.of("profilePending", Map.of("username", "nu"))));
        when(f.usersRepository.findById(104L)).thenReturn(Optional.of(u));
        lenient().doReturn(detail(4L, QueueStatus.APPROVED, QueueStage.HUMAN)).when(svc).getDetail(4L);
        assertEquals(QueueStatus.APPROVED, svc.autoApprove(4L, "ok", "t4").getStatus());

        ModerationQueueEntity rejectAgain = queue(5L, ModerationCaseType.CONTENT, ContentType.POST, 105L, QueueStatus.REJECTED);
        when(f.moderationQueueRepository.findById(5L)).thenReturn(Optional.of(rejectAgain));
        lenient().doReturn(detail(5L, QueueStatus.REJECTED, QueueStage.HUMAN)).when(svc).getDetail(5L);
        assertEquals(QueueStatus.REJECTED, svc.autoReject(5L, "bad", "t5").getStatus());

        ModerationQueueEntity alreadyApproved = queue(6L, ModerationCaseType.CONTENT, ContentType.POST, 106L, QueueStatus.APPROVED);
        when(f.moderationQueueRepository.findById(6L)).thenReturn(Optional.of(alreadyApproved));
        assertThrows(IllegalStateException.class, () -> svc.autoReject(6L, "bad", "t6"));
    }

    @Test
    void queueActions_shouldCoverClaimReleaseHumanRequeueBatchAndRiskTags() {
        Fixture f = new Fixture();
        mockLogin(f, 900L, "admin@example.com", "admin");
        AdminModerationQueueServiceImpl svc = spy(f.service);
        doReturn(detail(7L, QueueStatus.HUMAN, QueueStage.HUMAN)).when(svc).getDetail(7L);
        doReturn(detail(8L, QueueStatus.HUMAN, QueueStage.HUMAN)).when(svc).getDetail(8L);

        when(f.moderationQueueRepository.claimHuman(eq(7L), eq(900L), any())).thenReturn(1);
        when(f.moderationQueueRepository.releaseHuman(eq(8L), eq(900L), any())).thenReturn(1);
        assertEquals(7L, svc.claim(7L).getId());
        assertEquals(8L, svc.release(8L).getId());
        when(f.moderationQueueRepository.claimHuman(eq(70L), eq(900L), any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> svc.claim(70L));

        when(f.moderationQueueRepository.toHuman(eq(9L), eq(QueueStatus.HUMAN), eq(QueueStage.HUMAN), any())).thenReturn(1);
        doReturn(detail(9L, QueueStatus.HUMAN, QueueStage.HUMAN)).when(svc).getDetail(9L);
        assertEquals(9L, svc.toHuman(9L, "to human").getId());

        when(f.moderationQueueRepository.requeueToAutoWithReviewStage(eq(10L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("default"), any())).thenReturn(1);
        doReturn(detail(10L, QueueStatus.PENDING, QueueStage.RULE)).when(svc).getDetail(10L);
        assertEquals(10L, svc.requeueToAuto(10L, "r1", "default").getId());
        verify(f.moderationAutoKickService).kickQueueId(10L);

        when(f.moderationQueueRepository.requeueToAutoWithReviewStage(eq(20L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any())).thenReturn(1);
        when(f.moderationQueueRepository.requeueToAutoWithReviewStage(eq(21L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any())).thenReturn(0);
        var batch = svc.batchRequeueToAuto(Arrays.asList(20L, null, 21L, 20L), "rr", "reported");
        assertEquals(2, batch.getTotal());
        assertEquals(1, batch.getSuccess());
        assertEquals(1, batch.getFailed());
        verify(f.moderationAutoKickService).kickQueueId(20L);

        ModerationQueueEntity q = queue(30L, ModerationCaseType.CONTENT, ContentType.POST, 300L, QueueStatus.HUMAN);
        when(f.moderationQueueRepository.findById(30L)).thenReturn(Optional.of(q));
        when(f.riskLabelingService.getRiskTagSlugs(ContentType.POST, 300L)).thenReturn(List.of("a", "b"));
        assertEquals(List.of("a", "b"), svc.getRiskTags(30L));
        doReturn(detail(30L, QueueStatus.HUMAN, QueueStage.HUMAN)).when(svc).getDetail(30L);
        assertEquals(30L, svc.setRiskTags(30L, List.of("x")).getId());
        verify(f.riskLabelingService).replaceRiskTags(ContentType.POST, 300L, Source.HUMAN, List.of("x"), null, true);

        q.setStatus(QueueStatus.PENDING);
        assertThrows(IllegalStateException.class, () -> svc.setRiskTags(30L, List.of("x")));
    }

    @Test
    void backfillAndEnqueue_shouldCoverLimitWindowAndDryRunBranches() {
        Fixture f = new Fixture();
        mockLogin(f, 901L, "op@example.com", "op");

        when(f.postsRepository.findIdsByStatusAndIsDeletedFalse(PostStatus.PENDING)).thenReturn(List.of(11L, 12L, 13L));
        PostsEntity p1 = new PostsEntity();
        p1.setId(11L);
        p1.setStatus(PostStatus.PENDING);
        p1.setIsDeleted(false);
        p1.setCreatedAt(LocalDateTime.now().minusHours(2));
        PostsEntity p2 = new PostsEntity();
        p2.setId(12L);
        p2.setStatus(PostStatus.PUBLISHED);
        p2.setIsDeleted(false);
        p2.setCreatedAt(LocalDateTime.now());
        PostsEntity p3 = new PostsEntity();
        p3.setId(13L);
        p3.setStatus(PostStatus.PENDING);
        p3.setIsDeleted(true);
        p3.setCreatedAt(LocalDateTime.now());
        when(f.postsRepository.findById(11L)).thenReturn(Optional.of(p1));
        when(f.postsRepository.findById(12L)).thenReturn(Optional.of(p2));
        when(f.postsRepository.findById(13L)).thenReturn(Optional.of(p3));
        when(f.moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.POST, 11L)).thenReturn(Optional.empty());
        lenient().when(f.moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.POST, 12L)).thenReturn(Optional.empty());
        lenient().when(f.moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.POST, 13L)).thenReturn(Optional.empty());

        CommentsEntity c1 = new CommentsEntity();
        c1.setId(21L);
        when(f.commentsRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(c1)));
        when(f.moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.COMMENT, 21L)).thenReturn(Optional.empty());

        AdminModerationQueueBackfillRequest req = new AdminModerationQueueBackfillRequest();
        req.setDryRun(false);
        req.setLimit(10);
        req.setContentTypes(List.of(ContentType.POST, ContentType.COMMENT));
        req.setCreatedFrom(LocalDateTime.now().minusDays(1));
        req.setCreatedTo(LocalDateTime.now().plusDays(1));
        var out = f.service.backfill(req);
        assertEquals(2, out.getEnqueued());
        verify(f.moderationQueueRepository, org.mockito.Mockito.times(2)).save(any(ModerationQueueEntity.class));

        f.service.ensureEnqueuedPost(null);
        f.service.ensureEnqueuedComment(null);
        f.service.ensureEnqueuedProfile(null);
        verify(f.moderationQueueRepository, never()).findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.PROFILE, null);

        AdminModerationQueueBackfillRequest bad = new AdminModerationQueueBackfillRequest();
        bad.setCreatedFrom(LocalDateTime.now().plusDays(1));
        bad.setCreatedTo(LocalDateTime.now().minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> f.service.backfill(bad));
    }

    @Test
    void banUserAndOverrides_shouldCoverAuthorResolveAndStatusTransitions() {
        Fixture f = new Fixture();
        mockLogin(f, 902L, "mod@example.com", "moderator");
        AdminModerationQueueServiceImpl svc = spy(f.service);
        lenient().doReturn(detail(80L, QueueStatus.HUMAN, QueueStage.HUMAN)).when(svc).getDetail(80L);
        lenient().doReturn(detail(81L, QueueStatus.APPROVED, QueueStage.HUMAN)).when(svc).getDetail(81L);
        lenient().doReturn(detail(82L, QueueStatus.REJECTED, QueueStage.HUMAN)).when(svc).getDetail(82L);
        lenient().doReturn(detail(84L, QueueStatus.HUMAN, QueueStage.HUMAN)).when(svc).getDetail(84L);
        lenient().doReturn(detail(85L, QueueStatus.REJECTED, QueueStage.HUMAN)).when(svc).getDetail(85L);
        lenient().doReturn(detail(86L, QueueStatus.APPROVED, QueueStage.HUMAN)).when(svc).getDetail(86L);

        ModerationQueueEntity qp = queue(80L, ModerationCaseType.CONTENT, ContentType.POST, 800L, QueueStatus.HUMAN);
        when(f.moderationQueueRepository.findById(80L)).thenReturn(Optional.of(qp));
        PostsEntity p = new PostsEntity();
        p.setId(800L);
        p.setAuthorId(99L);
        when(f.postsRepository.findById(800L)).thenReturn(Optional.of(p));
        assertEquals(80L, svc.banUser(80L, "ban reason").getId());
        verify(f.usersService).banUser(99L, 902L, "moderator", "ban reason", "MODERATION_QUEUE", 80L);

        ModerationQueueEntity qProfile = queue(84L, ModerationCaseType.CONTENT, ContentType.PROFILE, 804L, QueueStatus.HUMAN);
        when(f.moderationQueueRepository.findById(84L)).thenReturn(Optional.of(qProfile));
        assertEquals(84L, svc.banUser(84L, "profile ban").getId());
        verify(f.usersService).banUser(804L, 902L, "moderator", "profile ban", "MODERATION_QUEUE", 84L);

        ModerationQueueEntity qa = queue(81L, ModerationCaseType.CONTENT, ContentType.POST, 801L, QueueStatus.APPROVED);
        when(f.moderationQueueRepository.findById(81L)).thenReturn(Optional.of(qa));
        assertEquals(81L, svc.overrideApprove(81L, "r").getId());
        doReturn(detail(82L, QueueStatus.APPROVED, QueueStage.HUMAN)).when(svc).approve(82L, "r2");
        ModerationQueueEntity qr = queue(82L, ModerationCaseType.CONTENT, ContentType.POST, 802L, QueueStatus.REJECTED);
        when(f.moderationQueueRepository.findById(82L)).thenReturn(Optional.of(qr));
        doReturn(detail(82L, QueueStatus.HUMAN, QueueStage.HUMAN)).when(svc).toHuman(82L, "r2");
        assertEquals(82L, svc.overrideApprove(82L, "r2").getId());

        ModerationQueueEntity qRejected = queue(85L, ModerationCaseType.CONTENT, ContentType.POST, 805L, QueueStatus.REJECTED);
        when(f.moderationQueueRepository.findById(85L)).thenReturn(Optional.of(qRejected));
        assertEquals(85L, svc.overrideReject(85L, "r3").getId());

        ModerationQueueEntity qApproved = queue(86L, ModerationCaseType.CONTENT, ContentType.POST, 806L, QueueStatus.APPROVED);
        when(f.moderationQueueRepository.findById(86L)).thenReturn(Optional.of(qApproved));
        doReturn(detail(86L, QueueStatus.HUMAN, QueueStage.HUMAN)).when(svc).toHuman(86L, "r4");
        doReturn(detail(86L, QueueStatus.REJECTED, QueueStage.HUMAN)).when(svc).reject(86L, "r4");
        assertEquals(86L, svc.overrideReject(86L, "r4").getId());

        assertThrows(IllegalArgumentException.class, () -> svc.banUser(83L, " "));
    }

    @Test
    void guardBranches_shouldCoverAuthAndInputValidationFailures() {
        Fixture f = new Fixture();

        assertThrows(RuntimeException.class, () -> f.service.claim(1L));

        mockLogin(f, 903L, "auditor@example.com", "auditor");
        when(f.administratorService.findByUsername("auditor@example.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> f.service.release(1L));

        mockLogin(f, 903L, "auditor@example.com", "auditor");
        assertThrows(IllegalArgumentException.class, () -> f.service.getRiskTags(null));
        when(f.moderationQueueRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> f.service.getRiskTags(404L));
        assertThrows(IllegalArgumentException.class, () -> f.service.toHuman(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> f.service.requeueToAuto(1L, "   ", "default"));
        assertThrows(IllegalArgumentException.class, () -> f.service.batchRequeueToAuto(Arrays.asList(null, -1L), "x", "default"));
        assertThrows(IllegalArgumentException.class, () -> f.service.batchRequeueToAuto(List.of(1L), "x", "bad-stage"));
    }

    @Test
    void sealRunningPipelineRun_shouldCoverNoRunNotRunningAndRunningBranches() throws Exception {
        Fixture f = new Fixture();
        invoke(f.service, "sealRunningPipelineRun", new Class[]{Long.class}, (Long) null);
        verify(f.moderationPipelineRunRepository, never()).findFirstByQueueIdOrderByCreatedAtDesc(anyLong());

        when(f.moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());
        invoke(f.service, "sealRunningPipelineRun", new Class[]{Long.class}, 1L);

        ModerationPipelineRunEntity done = new ModerationPipelineRunEntity();
        done.setStatus(ModerationPipelineRunEntity.RunStatus.SUCCESS);
        when(f.moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.of(done));
        invoke(f.service, "sealRunningPipelineRun", new Class[]{Long.class}, 2L);

        ModerationPipelineRunEntity running = new ModerationPipelineRunEntity();
        running.setStatus(ModerationPipelineRunEntity.RunStatus.RUNNING);
        running.setStartedAt(LocalDateTime.now().minusSeconds(1));
        when(f.moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(running));
        invoke(f.service, "sealRunningPipelineRun", new Class[]{Long.class}, 3L);
        verify(f.moderationPipelineRunRepository).save(running);
    }

    private static ModerationQueueEntity queue(Long id, ModerationCaseType caseType, ContentType ct, Long contentId, QueueStatus status) {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(id);
        q.setCaseType(caseType);
        q.setContentType(ct);
        q.setContentId(contentId);
        q.setStatus(status);
        q.setCurrentStage(QueueStage.HUMAN);
        q.setPriority(1);
        q.setCreatedAt(LocalDateTime.now().minusHours(1));
        q.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        return q;
    }

    private static AdminModerationQueueDetailDTO detail(Long id, QueueStatus status, QueueStage stage) {
        AdminModerationQueueDetailDTO d = new AdminModerationQueueDetailDTO();
        d.setId(id);
        d.setStatus(status);
        d.setCurrentStage(stage);
        d.setCaseType(ModerationCaseType.CONTENT);
        d.setContentType(ContentType.POST);
        d.setContentId(id + 100);
        return d;
    }

    private static void mockLogin(Fixture f, Long id, String email, String username) {
        UsersEntity actor = new UsersEntity();
        actor.setId(id);
        actor.setEmail(email);
        actor.setUsername(username);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "N/A", List.of()));
        when(f.administratorService.findByUsername(email)).thenReturn(Optional.of(actor));
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeStatic(String method, Class<?>[] pt, Object... args) throws Exception {
        Method m = AdminModerationQueueServiceImpl.class.getDeclaredMethod(method, pt);
        m.setAccessible(true);
        return (T) m.invoke(null, args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(AdminModerationQueueServiceImpl service, String method, Class<?>[] pt, Object... args) throws Exception {
        Method m = AdminModerationQueueServiceImpl.class.getDeclaredMethod(method, pt);
        m.setAccessible(true);
        return (T) m.invoke(service, args);
    }

    static class Fixture {
        final ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        final PostsRepository postsRepository = mock(PostsRepository.class);
        final PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        final FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        final CommentsRepository commentsRepository = mock(CommentsRepository.class);
        final UsersRepository usersRepository = mock(UsersRepository.class);
        final AdministratorService administratorService = mock(AdministratorService.class);
        final ReportsRepository reportsRepository = mock(ReportsRepository.class);
        final NotificationsService notificationsService = mock(NotificationsService.class);
        final ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);
        final ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        final ModerationAutoKickService moderationAutoKickService = mock(ModerationAutoKickService.class);
        final RiskLabelingService riskLabelingService = mock(RiskLabelingService.class);
        final ModerationChunkReviewService moderationChunkReviewService = mock(ModerationChunkReviewService.class);
        final RagPostIndexVisibilitySyncService ragPostIndexVisibilitySyncService = mock(RagPostIndexVisibilitySyncService.class);
        final RagCommentIndexVisibilitySyncService ragCommentIndexVisibilitySyncService = mock(RagCommentIndexVisibilitySyncService.class);
        final AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        final UsersService usersService = mock(UsersService.class);
        final AdminModerationQueueServiceImpl service = new AdminModerationQueueServiceImpl(
                moderationQueueRepository,
                postsRepository,
                postAttachmentsRepository,
                fileAssetExtractionsRepository,
                commentsRepository,
                usersRepository,
                administratorService,
                reportsRepository,
                notificationsService,
                moderationPipelineRunRepository,
                moderationActionsRepository,
                riskLabelingService,
                moderationChunkReviewService,
                ragPostIndexVisibilitySyncService,
                ragCommentIndexVisibilitySyncService,
                auditLogWriter,
                usersService
        );

        Fixture() {
            ReflectionTestUtils.setField(service, "moderationAutoKickService", moderationAutoKickService);

            lenient().when(moderationQueueRepository.save(any(ModerationQueueEntity.class))).thenAnswer(i -> i.getArgument(0));
            lenient().when(postsRepository.save(any(PostsEntity.class))).thenAnswer(i -> i.getArgument(0));
            lenient().when(commentsRepository.save(any(CommentsEntity.class))).thenAnswer(i -> i.getArgument(0));
            lenient().when(usersRepository.save(any(UsersEntity.class))).thenAnswer(i -> i.getArgument(0));
            lenient().when(riskLabelingService.getRiskTagItemsByTargets(any(), any())).thenReturn(Map.of());
            lenient().when(riskLabelingService.getRiskTagItems(any(), anyLong())).thenReturn(List.of());
            lenient().when(moderationChunkReviewService.loadProgressSummaries(any())).thenReturn(Map.of());
            lenient().when(reportsRepository.findByTargetTypeAndTargetId(any(), anyLong(), any())).thenReturn(new PageImpl<>(List.of()));
        }
    }
}
