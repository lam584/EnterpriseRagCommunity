package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationActionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.service.moderation.web.WebContentFetchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminModerationLlmContextBuilderBranchCoverageTest {

    static class Deps {
        final ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        final ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        final PostsRepository postsRepository = mock(PostsRepository.class);
        final CommentsRepository commentsRepository = mock(CommentsRepository.class);
        final ReportsRepository reportsRepository = mock(ReportsRepository.class);
        final ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        final PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        final FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        final UsersRepository usersRepository = mock(UsersRepository.class);
        final TagsRepository tagsRepository = mock(TagsRepository.class);
        final WebContentFetchService webContentFetchService = mock(WebContentFetchService.class);
        final AdminModerationLlmImageSupport imageSupport = mock(AdminModerationLlmImageSupport.class);
        final AdminModerationLlmContextBuilder builder = new AdminModerationLlmContextBuilder(
                queueRepository,
                policyConfigRepository,
                postsRepository,
                commentsRepository,
                reportsRepository,
                moderationActionsRepository,
                postAttachmentsRepository,
                fileAssetExtractionsRepository,
                usersRepository,
                tagsRepository,
                webContentFetchService,
                imageSupport
        );
    }

    @Test
    void resolvePromptVarsSafe_coversDirectNullAndPostCommentProfilePaths() {
        Deps d = new Deps();
        assertNull(d.builder.resolvePromptVarsSafe(null));

        LlmModerationTestRequest direct = new LlmModerationTestRequest();
        direct.setText("  direct text  ");
        PromptVars pv0 = d.builder.resolvePromptVarsSafe(direct);
        assertNotNull(pv0);
        assertEquals("  direct text  ", pv0.content());

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        assertNull(d.builder.resolvePromptVarsSafe(req));
        req.setQueueId(1L);
        assertNull(d.builder.resolvePromptVarsSafe(req));

        ModerationQueueEntity qPost = new ModerationQueueEntity();
        qPost.setId(1L);
        qPost.setContentType(ContentType.POST);
        qPost.setContentId(11L);
        when(d.queueRepository.findById(1L)).thenReturn(Optional.of(qPost));
        assertNull(d.builder.resolvePromptVarsSafe(req));

        PostsEntity post = new PostsEntity();
        post.setId(11L);
        post.setTitle(null);
        post.setContent(null);
        when(d.postsRepository.findById(11L)).thenReturn(Optional.of(post));
        when(d.postAttachmentsRepository.findByPostId(eq(11L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(d.webContentFetchService.extractUrls(any())).thenReturn(List.of());
        PromptVars postVars = d.builder.resolvePromptVarsSafe(req);
        assertNotNull(postVars);
        assertEquals("", postVars.title());
        assertTrue(postVars.text().contains("[POST]"));

        ModerationQueueEntity qComment = new ModerationQueueEntity();
        qComment.setId(2L);
        qComment.setContentType(ContentType.COMMENT);
        qComment.setContentId(21L);
        req.setQueueId(2L);
        when(d.queueRepository.findById(2L)).thenReturn(Optional.of(qComment));
        assertNull(d.builder.resolvePromptVarsSafe(req));

        CommentsEntity c = new CommentsEntity();
        c.setId(21L);
        c.setContent(null);
        when(d.commentsRepository.findById(21L)).thenReturn(Optional.of(c));
        PromptVars commentVars = d.builder.resolvePromptVarsSafe(req);
        assertNotNull(commentVars);
        assertEquals("", commentVars.content());

        ModerationQueueEntity qProfile = new ModerationQueueEntity();
        qProfile.setId(3L);
        qProfile.setCaseType(ModerationCaseType.CONTENT);
        qProfile.setContentType(ContentType.PROFILE);
        qProfile.setContentId(31L);
        req.setQueueId(3L);
        when(d.queueRepository.findById(3L)).thenReturn(Optional.of(qProfile));
        assertNull(d.builder.resolvePromptVarsSafe(req));

        UsersEntity u = new UsersEntity();
        u.setId(31L);
        u.setUsername("fallbackU");
        Map<String, Object> md = new LinkedHashMap<>();
        md.put("profilePending", Map.of("bio", "b", "location", "l", "website", "w", "avatarUrl", "a"));
        u.setMetadata(md);
        when(d.usersRepository.findById(31L)).thenReturn(Optional.of(u));
        PromptVars profileVars = d.builder.resolvePromptVarsSafe(req);
        assertNotNull(profileVars);
        assertTrue(profileVars.content().contains("username: fallbackU"));

        ModerationQueueEntity qNullType = new ModerationQueueEntity();
        qNullType.setId(4L);
        qNullType.setContentId(1L);
        req.setQueueId(4L);
        when(d.queueRepository.findById(4L)).thenReturn(Optional.of(qNullType));
        assertNull(d.builder.resolvePromptVarsSafe(req));
    }

    @Test
    void resolvePromptVars_coversPostCommentProfileWithReportsAndSnapshot() {
        Deps d = new Deps();
        when(d.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());

        ModerationQueueEntity qPost = new ModerationQueueEntity();
        qPost.setId(10L);
        qPost.setCaseType(ModerationCaseType.REPORT);
        qPost.setContentType(ContentType.POST);
        qPost.setContentId(110L);
        when(d.queueRepository.findById(10L)).thenReturn(Optional.of(qPost));

        PostsEntity p = new PostsEntity();
        p.setId(110L);
        p.setTitle("t");
        p.setContent("content");
        when(d.postsRepository.findById(110L)).thenReturn(Optional.of(p));

        PostAttachmentsEntity pa = new PostAttachmentsEntity();
        pa.setFileAssetId(901L);
        when(d.postAttachmentsRepository.findByPostId(eq(110L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pa)));
        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(901L);
        ex.setExtractStatus(FileAssetExtractionStatus.READY);
        ex.setExtractedText("ocr content");
        ex.setExtractedMetadataJson("{}");
        when(d.fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(ex));
        when(d.imageSupport.tryExtractDerivedImages(eq(901L), any(), eq(10))).thenReturn(List.of(new ImageRef(901L, "https://img/1", "image/png")));
        when(d.webContentFetchService.extractUrls(any())).thenReturn(List.of("https://a"));
        when(d.webContentFetchService.fetchUrlsToMeta(any())).thenReturn(Map.of("k", "v"));
        when(d.webContentFetchService.buildWebBlock(any())).thenReturn("[WEB]\nA");

        ReportsEntity rp = new ReportsEntity();
        rp.setStatus(ReportStatus.PENDING);
        rp.setReasonCode("spam");
        rp.setReasonText("bad");
        when(d.reportsRepository.findByTargetTypeAndTargetId(eq(ReportTargetType.POST), eq(110L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rp)));

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(10L);
        PromptVars post = d.builder.resolvePromptVars(req);
        assertNotNull(post);
        assertTrue(post.text().contains("[REPORTS]"));
        assertTrue(post.content().contains("ocr content"));

        ModerationQueueEntity qComment = new ModerationQueueEntity();
        qComment.setId(11L);
        qComment.setCaseType(ModerationCaseType.REPORT);
        qComment.setContentType(ContentType.COMMENT);
        qComment.setContentId(120L);
        when(d.queueRepository.findById(11L)).thenReturn(Optional.of(qComment));
        CommentsEntity c = new CommentsEntity();
        c.setId(120L);
        c.setContent("c1");
        when(d.commentsRepository.findById(120L)).thenReturn(Optional.of(c));
        when(d.reportsRepository.findByTargetTypeAndTargetId(eq(ReportTargetType.COMMENT), eq(120L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        req.setQueueId(11L);
        PromptVars comment = d.builder.resolvePromptVars(req);
        assertNotNull(comment);
        assertEquals("c1", comment.content());

        ModerationQueueEntity qProfile = new ModerationQueueEntity();
        qProfile.setId(12L);
        qProfile.setCaseType(ModerationCaseType.REPORT);
        qProfile.setContentType(ContentType.PROFILE);
        qProfile.setContentId(130L);
        when(d.queueRepository.findById(12L)).thenReturn(Optional.of(qProfile));

        UsersEntity u = new UsersEntity();
        u.setId(130L);
        u.setUsername("U0");
        u.setMetadata(Map.of("profile", Map.of("username", "   ", "bio", "b1")));
        when(d.usersRepository.findById(130L)).thenReturn(Optional.of(u));
        req.setQueueId(12L);
        req.setReviewStage("reported");

        ModerationActionsEntity a = new ModerationActionsEntity();
        a.setQueueId(12L);
        a.setReason("REPORT_SNAPSHOT");
        a.setCreatedAt(LocalDateTime.now());
        a.setSnapshot(Map.of("target_snapshot", Map.of("username", "snapU", "bio", "snapB")));
        when(d.moderationActionsRepository.findAllByQueueId(12L)).thenReturn(List.of(a));
        ReportsEntity rr = new ReportsEntity();
        rr.setStatus(ReportStatus.REVIEWING);
        rr.setReasonCode("abuse");
        rr.setReasonText("");
        when(d.reportsRepository.findByTargetTypeAndTargetId(eq(ReportTargetType.PROFILE), eq(130L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rr)));

        PromptVars profile = d.builder.resolvePromptVars(req);
        assertNotNull(profile);
        assertTrue(profile.content().contains("snapB"));
        assertTrue(profile.text().contains("[REPORTS]"));

        ModerationQueueEntity qNone = new ModerationQueueEntity();
        qNone.setId(13L);
        when(d.queueRepository.findById(13L)).thenReturn(Optional.of(qNone));
        req.setQueueId(13L);
        assertNull(d.builder.resolvePromptVars(req));
    }

    @Test
    void buildQueueTraceLine_coversPostAndExtractionBranches() {
        Deps d = new Deps();
        assertNull(d.builder.buildQueueTraceLine(null));

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        assertNull(d.builder.buildQueueTraceLine(req));
        req.setQueueId(77L);
        assertNull(d.builder.buildQueueTraceLine(req));

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(77L);
        q.setContentType(ContentType.POST);
        q.setContentId(771L);
        when(d.queueRepository.findById(77L)).thenReturn(Optional.of(q));

        List<PostAttachmentsEntity> atts = new ArrayList<>();
        atts.add(null);
        for (long i = 1; i <= 21; i++) {
            PostAttachmentsEntity a = new PostAttachmentsEntity();
            a.setFileAssetId(i);
            atts.add(a);
        }
        when(d.postAttachmentsRepository.findByPostId(eq(771L), any(Pageable.class))).thenReturn(new PageImpl<>(atts));

        List<FileAssetExtractionsEntity> exs = new ArrayList<>();
        FileAssetExtractionsEntity ex1 = new FileAssetExtractionsEntity();
        ex1.setFileAssetId(1L);
        ex1.setExtractStatus(null);
        ex1.setExtractedText(null);
        exs.add(ex1);
        FileAssetExtractionsEntity ex2 = new FileAssetExtractionsEntity();
        ex2.setFileAssetId(2L);
        ex2.setExtractStatus(FileAssetExtractionStatus.READY);
        ex2.setExtractedText("abc");
        exs.add(ex2);
        exs.add(null);
        when(d.fileAssetExtractionsRepository.findAllById(any())).thenReturn(exs);

        String trace = d.builder.buildQueueTraceLine(req);
        assertNotNull(trace);
        assertTrue(trace.contains("TRACE queueId=77"));
        assertTrue(trace.contains("postId=771"));
        assertTrue(trace.contains("..."));

        doThrow(new RuntimeException("x")).when(d.fileAssetExtractionsRepository).findAllById(any());
        String trace2 = d.builder.buildQueueTraceLine(req);
        assertNotNull(trace2);
    }

    @Test
    void buildPolicyContextBlock_coversUseQueueAndSerializationBranches() throws Exception {
        Deps d = new Deps();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(1L);

        assertNull(d.builder.buildPolicyContextBlock(req, false));
        assertNull(d.builder.buildPolicyContextBlock(null, true));
        assertNull(d.builder.buildPolicyContextBlock(new LlmModerationTestRequest(), true));

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(1L);
        when(d.queueRepository.findById(1L)).thenReturn(Optional.of(q));
        assertNull(d.builder.buildPolicyContextBlock(req, true));

        q.setContentType(ContentType.POST);
        assertNull(d.builder.buildPolicyContextBlock(req, true));

        ModerationPolicyConfigEntity policy = new ModerationPolicyConfigEntity();
        policy.setPolicyVersion("   ");
        when(d.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy));
        assertNull(d.builder.buildPolicyContextBlock(req, true));

        policy.setPolicyVersion(" v1 ");
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);
        policy.setConfig(cyclic);
        assertNull(d.builder.buildPolicyContextBlock(req, true));

        policy.setConfig(Map.of("a", 1));
        req.setReviewStage(" reported ");
        String block = d.builder.buildPolicyContextBlock(req, true);
        assertNotNull(block);
        assertTrue(block.contains("policy_version=v1"));
        assertTrue(block.contains("review_stage=reported"));
    }

    @Test
    void buildReportsBlock_coversNullEmptyStatusFilteringAndLimit() {
        Deps d = new Deps();
        assertNull(d.builder.buildReportsBlock(null, 1L));
        assertNull(d.builder.buildReportsBlock(ReportTargetType.POST, null));

        when(d.reportsRepository.findByTargetTypeAndTargetId(eq(ReportTargetType.POST), eq(9L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertNull(d.builder.buildReportsBlock(ReportTargetType.POST, 9L));

        ReportsEntity r1 = new ReportsEntity();
        r1.setStatus(ReportStatus.RESOLVED);
        r1.setReasonCode("x");
        ReportsEntity r2 = new ReportsEntity();
        r2.setStatus(ReportStatus.PENDING);
        r2.setReasonCode("  ");
        r2.setReasonText(" ");
        ReportsEntity r3 = new ReportsEntity();
        r3.setStatus(ReportStatus.PENDING);
        r3.setReasonCode("code1");
        r3.setReasonText("text1");
        ReportsEntity r4 = new ReportsEntity();
        r4.setStatus(ReportStatus.REVIEWING);
        r4.setReasonCode("code2");
        r4.setReasonText("");
        ReportsEntity r5 = new ReportsEntity();
        r5.setStatus(ReportStatus.REVIEWING);
        r5.setReasonCode("code3");
        r5.setReasonText("text3");
        ReportsEntity r6 = new ReportsEntity();
        r6.setStatus(ReportStatus.REVIEWING);
        r6.setReasonCode("code4");
        r6.setReasonText("text4");

        when(d.reportsRepository.findByTargetTypeAndTargetId(eq(ReportTargetType.POST), eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.Arrays.asList(null, r1, r2, r3, r4, r5, r6)));
        String out = d.builder.buildReportsBlock(ReportTargetType.POST, 10L);
        assertNotNull(out);
        assertTrue(out.contains("code1"));
        assertTrue(out.contains("code2"));
        assertTrue(out.contains("code3"));
        assertTrue(!out.contains("code4"));
    }

    @Test
    void resolveQueueCtxUserContextAndOcr_coverBranches() {
        Deps d = new Deps();
        assertNull(d.builder.resolveQueueCtx(null, true));
        assertNull(d.builder.resolveQueueCtx(new LlmModerationTestRequest(), true));
        assertNull(d.builder.resolveQueueCtx(new LlmModerationTestRequest(), false));

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(5L);
        assertNull(d.builder.resolveQueueCtx(req, true));

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(5L);
        when(d.queueRepository.findById(5L)).thenReturn(Optional.of(q));
        assertNull(d.builder.resolveQueueCtx(req, true));

        q.setContentType(ContentType.POST);
        assertNull(d.builder.resolveQueueCtx(req, true));
        q.setContentId(500L);
        QueueCtx ctxNoPolicy = d.builder.resolveQueueCtx(req, true);
        assertNotNull(ctxNoPolicy);
        assertNotNull(ctxNoPolicy.policyConfig());

        ModerationPolicyConfigEntity policy = new ModerationPolicyConfigEntity();
        policy.setContentType(ContentType.POST);
        policy.setPolicyVersion(" v2 ");
        policy.setConfig(Map.of("k", "v"));
        when(d.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy));
        QueueCtx ctx = d.builder.resolveQueueCtx(req, true);
        assertNotNull(ctx);
        assertEquals("v2", ctx.policyVersion());

        assertNull(d.builder.resolveUserContext(null));
        assertNull(d.builder.resolveUserContext(new QueueCtx(null, null, "v", Map.of())));

        PostsEntity p = new PostsEntity();
        p.setId(500L);
        p.setAuthorId(700L);
        when(d.postsRepository.findById(500L)).thenReturn(Optional.of(p));
        UsersEntity u = new UsersEntity();
        u.setId(700L);
        u.setCreatedAt(LocalDateTime.now().minusHours(1));
        u.setMetadata(Map.of("risk_score", 0.8, "domain", "d1"));
        when(d.usersRepository.findById(700L)).thenReturn(Optional.of(u));
        Map<String, Object> uc = d.builder.resolveUserContext(ctx);
        assertNotNull(uc);
        assertEquals("d1", uc.get("domain"));

        q.setContentType(ContentType.COMMENT);
        q.setContentId(600L);
        CommentsEntity c = new CommentsEntity();
        c.setId(600L);
        c.setAuthorId(701L);
        when(d.commentsRepository.findById(600L)).thenReturn(Optional.of(c));
        when(d.usersRepository.findById(701L)).thenReturn(Optional.empty());
        assertNull(d.builder.resolveUserContext(new QueueCtx(q, null, "v", Map.of())));

        q.setContentType(ContentType.PROFILE);
        q.setContentId(702L);
        UsersEntity u2 = new UsersEntity();
        u2.setId(702L);
        u2.setMetadata(Map.of("domain", "   "));
        when(d.usersRepository.findById(702L)).thenReturn(Optional.of(u2));
        assertNull(d.builder.resolveUserContext(new QueueCtx(q, null, "v", Map.of())));

        assertEquals(0, d.builder.resolveRelatedOcr(null).size());
        assertEquals(0, d.builder.resolveRelatedOcr(new QueueCtx(new ModerationQueueEntity(), null, "v", Map.of())).size());

        ModerationQueueEntity qPost = new ModerationQueueEntity();
        qPost.setContentType(ContentType.POST);
        qPost.setContentId(800L);
        PostAttachmentsEntity pa = new PostAttachmentsEntity();
        pa.setFileAssetId(88L);
        when(d.postAttachmentsRepository.findByPostId(eq(800L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pa)));
        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(88L);
        ex.setExtractedText("  ocr1  ");
        when(d.fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(ex));
        List<Map<String, Object>> ocrs = d.builder.resolveRelatedOcr(new QueueCtx(qPost, null, "v", Map.of()));
        assertEquals(1, ocrs.size());

        doThrow(new RuntimeException("x")).when(d.postAttachmentsRepository).findByPostId(eq(800L), any(Pageable.class));
        List<Map<String, Object>> ocrs2 = d.builder.resolveRelatedOcr(new QueueCtx(qPost, null, "v", Map.of()));
        assertEquals(0, ocrs2.size());
    }

    @Test
    void buildTextAuditInputJson_coversPostCommentProfileAndErrorBranches() throws Exception {
        Deps d = new Deps();
        assertNull(d.builder.buildTextAuditInputJson(null, null, null));

        ModerationQueueEntity invalidQ = new ModerationQueueEntity();
        QueueCtx invalidCtx = new QueueCtx(invalidQ, null, "v", Map.of());
        assertNull(d.builder.buildTextAuditInputJson(null, null, invalidCtx));

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(200L);
        q.setCaseType(ModerationCaseType.REPORT);
        q.setContentType(ContentType.POST);
        q.setContentId(201L);
        QueueCtx ctx = new QueueCtx(q, null, " ", Map.of());
        assertNull(d.builder.buildTextAuditInputJson(null, null, ctx));

        TagsEntity t1 = new TagsEntity();
        t1.setSlug("r1");
        t1.setName("n1");
        TagsEntity t2 = new TagsEntity();
        t2.setSlug("r2");
        t2.setName(" ");
        when(d.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(java.util.Arrays.asList(null, t1, t2));

        PostsEntity p = new PostsEntity();
        p.setId(201L);
        p.setAuthorId(301L);
        when(d.postsRepository.findById(201L)).thenReturn(Optional.of(p));
        UsersEntity author = new UsersEntity();
        author.setId(301L);
        author.setCreatedAt(LocalDateTime.now().minusHours(2));
        author.setMetadata(Map.of("risk_score", 0.9, "domain", "siteA"));
        when(d.usersRepository.findById(301L)).thenReturn(Optional.of(author));
        when(d.postAttachmentsRepository.findByPostId(eq(201L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        ReportsEntity r = new ReportsEntity();
        r.setId(1L);
        r.setReporterId(9001L);
        r.setStatus(ReportStatus.PENDING);
        r.setReasonCode("rc1");
        r.setCreatedAt(LocalDateTime.now());
        when(d.reportsRepository.findByTargetTypeAndTargetId(eq(ReportTargetType.POST), eq(201L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r)));
        UsersEntity reporter = new UsersEntity();
        reporter.setId(9001L);
        reporter.setMetadata(Map.of("trust_score", 0.7));
        when(d.usersRepository.findById(9001L)).thenReturn(Optional.of(reporter));
        when(d.moderationActionsRepository.findAllByQueueId(200L)).thenReturn(List.of());

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setReviewStage("reported");
        PromptVars vars = new PromptVars("tt", "cc", "tx");
        String postJson = d.builder.buildTextAuditInputJson(req, vars, new QueueCtx(q, null, "v1", Map.of("review_trigger", Map.of("window_minutes", 5))));
        assertNotNull(postJson);
        JsonNode postNode = new ObjectMapper().readTree(postJson);
        assertEquals("TextAudit", postNode.get("task").asText());
        assertNotNull(postNode.get("report_context"));
        assertNotNull(postNode.get("label_taxonomy"));

        q.setContentType(ContentType.COMMENT);
        q.setContentId(202L);
        CommentsEntity c = new CommentsEntity();
        c.setId(202L);
        c.setPostId(201L);
        c.setAuthorId(301L);
        c.setParentId(203L);
        c.setContent(" comment ");
        when(d.commentsRepository.findById(202L)).thenReturn(Optional.of(c));
        CommentsEntity replied = new CommentsEntity();
        replied.setId(203L);
        replied.setContent("reply text");
        when(d.commentsRepository.findById(203L)).thenReturn(Optional.of(replied));
        String commentJson = d.builder.buildTextAuditInputJson(new LlmModerationTestRequest(), new PromptVars("", "ctx", "tx"), new QueueCtx(q, null, "v1", Map.of()));
        assertNotNull(commentJson);
        JsonNode commentNode = new ObjectMapper().readTree(commentJson);
        assertEquals(202L, commentNode.get("comment_id").asLong());
        assertEquals("publish", commentNode.get("comment_stage").asText());

        q.setContentType(ContentType.PROFILE);
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentId(303L);
        UsersEntity profileUser = new UsersEntity();
        profileUser.setId(303L);
        profileUser.setUsername("un");
        profileUser.setMetadata(Map.of(
                "profile", Map.of("bio", "b0"),
                "profilePending", Map.of("username", "up", "bio", "bp", "location", "lp", "website", "wp", "avatarUrl", "ap")
        ));
        when(d.usersRepository.findById(303L)).thenReturn(Optional.of(profileUser));
        String profileJson = d.builder.buildTextAuditInputJson(new LlmModerationTestRequest(), null, new QueueCtx(q, null, "v1", Map.of()));
        assertNotNull(profileJson);
        JsonNode profileNode = new ObjectMapper().readTree(profileJson);
        assertNotNull(profileNode.get("profile_fields"));
        assertNotNull(profileNode.get("old_profile_fields"));

        setObjectMapperToThrow(d.builder);
        assertNull(d.builder.buildTextAuditInputJson(new LlmModerationTestRequest(), null, new QueueCtx(q, null, "v1", Map.of())));
    }

    @Test
    void buildVisionAuditInputJsonList_andBuildJudgeInputJson_coverBranches() throws Exception {
        Deps d = new Deps();
        assertNull(d.builder.buildVisionAuditInputJsonList(null, null, null));

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setContentType(ContentType.POST);
        q.setContentId(1L);
        QueueCtx badPolicy = new QueueCtx(q, null, " ", Map.of());
        assertNull(d.builder.buildVisionAuditInputJsonList(null, badPolicy, List.of()));

        TagsEntity tag = new TagsEntity();
        tag.setSlug("slug1");
        tag.setName("name1");
        when(d.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(tag));

        QueueCtx okCtx = new QueueCtx(q, null, "v1", Map.of());
        List<ImageRef> images = new ArrayList<>();
        images.add(null);
        images.add(new ImageRef(1L, "   ", "image/png"));
        for (int i = 0; i < 55; i++) {
            images.add(new ImageRef((long) i, "https://img/" + i, "image/png"));
        }
        String vision = d.builder.buildVisionAuditInputJsonList(new LlmModerationTestRequest(), okCtx, images);
        assertNotNull(vision);
        JsonNode arr = new ObjectMapper().readTree(vision);
        assertEquals(50, arr.size());

        setObjectMapperToThrow(d.builder);
        assertNull(d.builder.buildVisionAuditInputJsonList(new LlmModerationTestRequest(), okCtx, images));

        Deps d2 = new Deps();
        assertNull(d2.builder.buildJudgeInputJson(null, null, null, null, null, null, null, null, null, null, null));
        assertNull(d2.builder.buildJudgeInputJson(new QueueCtx(q, null, null, Map.of()), null, null, null, null, null, null, null, null, null, null));

        when(d2.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(tag));
        String judge = d2.builder.buildJudgeInputJson(
                new QueueCtx(q, null, "pv", Map.of("thresholds", Map.of("t", 1), "escalate_rules", List.of("r1"))),
                "contentA",
                "desc",
                0.2,
                0.3,
                List.of("tr"),
                List.of("ir"),
                List.of("te"),
                List.of("ie"),
                "POST",
                99L
        );
        assertNotNull(judge);
        JsonNode root = new ObjectMapper().readTree(judge);
        assertEquals("multimodal", root.get("judge_mode").asText());
        assertEquals("post", root.get("content_type").asText());

        setObjectMapperToThrow(d2.builder);
        assertNull(d2.builder.buildJudgeInputJson(
                new QueueCtx(q, null, "pv", Map.of()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1L
        ));
    }

    @Test
    void privateHelpersAndStatics_coverRemainingBranches() throws Exception {
        Deps d = new Deps();

        Method thread = AdminModerationLlmContextBuilder.class.getDeclaredMethod("buildThreadContextForComment", Long.class, Long.class);
        thread.setAccessible(true);
        assertNull(thread.invoke(d.builder, null, null));

        PostsEntity p = new PostsEntity();
        p.setId(11L);
        p.setTitle(" ");
        p.setContent("x".repeat(401));
        when(d.postsRepository.findById(11L)).thenReturn(Optional.of(p));
        CommentsEntity c = new CommentsEntity();
        c.setId(12L);
        c.setContent(" y ".repeat(210));
        when(d.commentsRepository.findById(12L)).thenReturn(Optional.of(c));
        Object threadOut = thread.invoke(d.builder, 11L, 12L);
        assertNotNull(threadOut);

        Method reportCtx = AdminModerationLlmContextBuilder.class.getDeclaredMethod(
                "buildReportContext",
                ReportTargetType.class,
                Long.class,
                QueueCtx.class,
                String.class
        );
        reportCtx.setAccessible(true);
        assertNull(reportCtx.invoke(d.builder, null, 1L, null, "reported"));
        assertNull(reportCtx.invoke(d.builder, ReportTargetType.POST, 1L, null, "publish"));

        ReportsEntity old = new ReportsEntity();
        old.setId(1L);
        old.setReporterId(501L);
        old.setStatus(ReportStatus.PENDING);
        old.setReasonCode(" ");
        old.setCreatedAt(LocalDateTime.now().minusHours(3));
        ReportsEntity now = new ReportsEntity();
        now.setId(2L);
        now.setReporterId(502L);
        now.setStatus(ReportStatus.REVIEWING);
        now.setReasonCode("codeX");
        now.setCreatedAt(LocalDateTime.now());
        when(d.reportsRepository.findByTargetTypeAndTargetId(eq(ReportTargetType.POST), eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.Arrays.asList(null, old, now)));

        UsersEntity ru = new UsersEntity();
        ru.setId(502L);
        ru.setMetadata(Map.of("trust_score", 2.0));
        when(d.usersRepository.findById(502L)).thenReturn(Optional.of(ru));

        ModerationQueueEntity mq = new ModerationQueueEntity();
        mq.setId(900L);
        mq.setContentType(ContentType.POST);
        mq.setContentId(1L);
        mq.setUpdatedAt(LocalDateTime.now());
        QueueCtx rcCtx = new QueueCtx(mq, null, "v", Map.of("review_trigger", Map.of("window_minutes", "9")));
        ModerationActionsEntity ma = new ModerationActionsEntity();
        ma.setQueueId(900L);
        ma.setCreatedAt(LocalDateTime.now());
        ma.setSnapshot(Map.of("content_snapshot_id", "sid-1"));
        when(d.moderationActionsRepository.findAllByQueueId(900L)).thenReturn(List.of(ma));

        Object rc = reportCtx.invoke(d.builder, ReportTargetType.POST, 1L, rcCtx, "reported");
        assertNotNull(rc);
        assertTrue(((Map<?, ?>) rc).containsKey("report_count_total"));

        doThrow(new RuntimeException("x")).when(d.moderationActionsRepository).findAllByQueueId(900L);
        Object rc2 = reportCtx.invoke(d.builder, ReportTargetType.POST, 1L, rcCtx, "reported");
        assertNotNull(rc2);

        Method snapshot = AdminModerationLlmContextBuilder.class.getDeclaredMethod("resolveSnapshotId", ModerationQueueEntity.class);
        snapshot.setAccessible(true);
        assertNull(snapshot.invoke(d.builder, new Object[]{null}));
        ModerationQueueEntity badQ = new ModerationQueueEntity();
        assertNull(snapshot.invoke(d.builder, badQ));

        ModerationQueueEntity q2 = new ModerationQueueEntity();
        q2.setId(901L);
        q2.setContentType(ContentType.COMMENT);
        q2.setContentId(44L);
        ModerationActionsEntity a1 = new ModerationActionsEntity();
        a1.setSnapshot(Map.of("content_snapshot_id", " ", "x", 1));
        ModerationActionsEntity a2 = new ModerationActionsEntity();
        a2.setCreatedAt(LocalDateTime.now());
        a2.setSnapshot(Map.of("content_snapshot_id", "SID2"));
        when(d.moderationActionsRepository.findAllByQueueId(901L)).thenReturn(List.of(a1, a2));
        assertEquals("SID2", snapshot.invoke(d.builder, q2));

        Method profileSnap = AdminModerationLlmContextBuilder.class.getDeclaredMethod("resolveReportProfileSnapshotFields", ModerationQueueEntity.class);
        profileSnap.setAccessible(true);
        assertNull(profileSnap.invoke(d.builder, new Object[]{null}));
        ModerationQueueEntity qp = new ModerationQueueEntity();
        qp.setId(333L);
        when(d.moderationActionsRepository.findAllByQueueId(333L)).thenReturn(List.of());
        assertNull(profileSnap.invoke(d.builder, qp));
        ModerationActionsEntity p1 = new ModerationActionsEntity();
        p1.setReason("REPORT_SNAPSHOT");
        p1.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        p1.setSnapshot(Map.of("target_snapshot", Map.of("username", "u1")));
        ModerationActionsEntity p2 = new ModerationActionsEntity();
        p2.setReason("REPORT_SNAPSHOT");
        p2.setCreatedAt(LocalDateTime.now());
        p2.setSnapshot(Map.of("target_snapshot", Map.of("username", "u2")));
        when(d.moderationActionsRepository.findAllByQueueId(333L)).thenReturn(List.of(p1, p2));
        Map<?, ?> snap = (Map<?, ?>) profileSnap.invoke(d.builder, qp);
        assertEquals("u2", snap.get("username"));

        Method synthetic = AdminModerationLlmContextBuilder.class.getDeclaredMethod("buildSyntheticSnapshotId", ModerationQueueEntity.class);
        synthetic.setAccessible(true);
        assertNull(synthetic.invoke(null, new Object[]{null}));
        assertNull(synthetic.invoke(null, new ModerationQueueEntity()));
        q2.setUpdatedAt(LocalDateTime.now());
        Object sid = synthetic.invoke(null, q2);
        assertTrue(String.valueOf(sid).contains("moderation:comment:44:queue:901"));

        Method deepGet = AdminModerationLlmContextBuilder.class.getDeclaredMethod("deepGet", Map.class, String.class);
        deepGet.setAccessible(true);
        assertNull(deepGet.invoke(null, null, "a"));
        assertEquals(1, deepGet.invoke(null, Map.of("a", Map.of("b", 1)), "a.b"));
        assertNull(deepGet.invoke(null, Map.of("a", 1), "a.b"));

        Method asMap = AdminModerationLlmContextBuilder.class.getDeclaredMethod("asMap", Object.class);
        asMap.setAccessible(true);
        assertNull(asMap.invoke(null, "x"));
        Map<?, ?> am = (Map<?, ?>) asMap.invoke(null, Map.of(1, "x", "k", "v"));
        assertEquals("x", am.get("1"));

        Method asStringList = AdminModerationLlmContextBuilder.class.getDeclaredMethod("asStringList", Object.class);
        asStringList.setAccessible(true);
        assertEquals(0, ((List<?>) asStringList.invoke(null, new Object[]{null})).size());
        assertEquals(1, ((List<?>) asStringList.invoke(null, List.of(" ", "x"))).size());
        assertEquals(1, ((List<?>) asStringList.invoke(null, "v")).size());

        Method safeString = AdminModerationLlmContextBuilder.class.getDeclaredMethod("safeString", Object.class);
        safeString.setAccessible(true);
        assertNull(safeString.invoke(null, new Object[]{null}));
        assertNull(safeString.invoke(null, "   "));
        assertEquals("A", safeString.invoke(null, " A "));

        Method nullToEmpty = AdminModerationLlmContextBuilder.class.getDeclaredMethod("nullToEmpty", String.class);
        nullToEmpty.setAccessible(true);
        assertEquals("", nullToEmpty.invoke(null, new Object[]{null}));
        assertEquals("x", nullToEmpty.invoke(null, "x"));

        Method clamp01 = AdminModerationLlmContextBuilder.class.getDeclaredMethod("clamp01", Double.class);
        clamp01.setAccessible(true);
        assertEquals(0.5, (Double) clamp01.invoke(null, new Object[]{null}), 1e-9);
        assertEquals(0.0, (Double) clamp01.invoke(null, -1.0), 1e-9);
        assertEquals(1.0, (Double) clamp01.invoke(null, 2.0), 1e-9);
        assertEquals(0.3, (Double) clamp01.invoke(null, 0.3), 1e-9);

        Method blankToNullS = AdminModerationLlmContextBuilder.class.getDeclaredMethod("blankToNull", String.class);
        blankToNullS.setAccessible(true);
        assertNull(blankToNullS.invoke(null, new Object[]{null}));
        assertNull(blankToNullS.invoke(null, " "));
        assertEquals("x", blankToNullS.invoke(null, " x "));

        Method blankToNullO = AdminModerationLlmContextBuilder.class.getDeclaredMethod("blankToNull", Object.class);
        blankToNullO.setAccessible(true);
        assertNull(blankToNullO.invoke(null, new Object[]{null}));
        assertNull(blankToNullO.invoke(null, " "));
        assertEquals("x", blankToNullO.invoke(null, " x "));

        Method parseJson = AdminModerationLlmContextBuilder.class.getDeclaredMethod("parseJson", ObjectMapper.class, String.class);
        parseJson.setAccessible(true);
        assertNotNull(parseJson.invoke(null, new ObjectMapper(), "{\"a\":1}"));
        assertNull(parseJson.invoke(null, new ObjectMapper(), "{x"));
    }

    private static void setObjectMapperToThrow(AdminModerationLlmContextBuilder builder) throws Exception {
        Field f = AdminModerationLlmContextBuilder.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        ObjectMapper m = mock(ObjectMapper.class);
        when(m.writeValueAsString(any())).thenThrow(new RuntimeException("json fail"));
        f.set(builder, m);
    }
}
