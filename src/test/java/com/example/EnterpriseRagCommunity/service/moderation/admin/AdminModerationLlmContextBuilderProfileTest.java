package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationActionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminModerationLlmContextBuilderProfileTest {

    private static AdminModerationLlmContextBuilder newBuilder(
            ModerationQueueRepository queueRepository,
            ModerationPolicyConfigRepository policyConfigRepository,
            PostsRepository postsRepository,
            CommentsRepository commentsRepository,
            ReportsRepository reportsRepository,
            ModerationActionsRepository moderationActionsRepository,
            PostAttachmentsRepository postAttachmentsRepository,
            FileAssetExtractionsRepository fileAssetExtractionsRepository,
            UsersRepository usersRepository,
            TagsRepository tagsRepository,
            WebContentFetchService webContentFetchService,
            AdminModerationLlmImageSupport imageSupport
    ) {
        return new AdminModerationLlmContextBuilder(
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
    void profileTextAudit_shouldUseProfilePending_andIncludeOldProfileFields() throws Exception {
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);
        WebContentFetchService webContentFetchService = mock(WebContentFetchService.class);
        AdminModerationLlmImageSupport imageSupport = mock(AdminModerationLlmImageSupport.class);

        when(tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());

        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setUsername("oldName");
        u.setCreatedAt(LocalDateTime.now().minusDays(30));

        Map<String, Object> publicProfile = new LinkedHashMap<>();
        publicProfile.put("bio", "oldBio");
        publicProfile.put("location", "oldLoc");
        publicProfile.put("website", "oldWeb");
        publicProfile.put("avatarUrl", "oldAvatar");

        Map<String, Object> pendingProfile = new LinkedHashMap<>();
        pendingProfile.put("username", "newName");
        pendingProfile.put("bio", "newBio");
        pendingProfile.put("location", "newLoc");
        pendingProfile.put("website", "newWeb");
        pendingProfile.put("avatarUrl", "newAvatar");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profile", publicProfile);
        metadata.put("profilePending", pendingProfile);
        u.setMetadata(metadata);

        when(usersRepository.findById(1L)).thenReturn(Optional.of(u));
        when(reportsRepository.findByTargetTypeAndTargetId(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        AdminModerationLlmContextBuilder builder = newBuilder(
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

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(10L);
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.PROFILE);
        q.setContentId(1L);

        QueueCtx ctx = new QueueCtx(q, new ModerationPolicyConfigEntity(), "v1", Map.of());

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setReviewStage(null);

        String json = builder.buildTextAuditInputJson(req, null, ctx);
        assertNotNull(json);

        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(json);
        JsonNode pf = root.get("profile_fields");
        assertNotNull(pf);
        assertEquals("newName", pf.get("username").asText());
        assertEquals("newBio", pf.get("bio").asText());
        assertEquals("newLoc", pf.get("location").asText());
        assertEquals("newWeb", pf.get("website").asText());
        assertEquals("newAvatar", pf.get("avatarUrl").asText());

        JsonNode old = root.get("old_profile_fields");
        assertNotNull(old);
        assertEquals("oldName", old.get("username").asText());
        assertEquals("oldBio", old.get("bio").asText());
        assertEquals("oldLoc", old.get("location").asText());
        assertEquals("oldWeb", old.get("website").asText());
        assertEquals("oldAvatar", old.get("avatarUrl").asText());
    }

    @Test
    void profilePromptVars_andTextAudit_shouldUseReportSnapshot_onReportedReview() throws Exception {
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationActionsRepository moderationActionsRepository = mock(ModerationActionsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);
        WebContentFetchService webContentFetchService = mock(WebContentFetchService.class);
        AdminModerationLlmImageSupport imageSupport = mock(AdminModerationLlmImageSupport.class);

        when(tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(99L);
        q.setCaseType(ModerationCaseType.REPORT);
        q.setContentType(ContentType.PROFILE);
        q.setContentId(1L);

        when(queueRepository.findById(99L)).thenReturn(Optional.of(q));

        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setUsername("liveName");
        Map<String, Object> publicProfile = new LinkedHashMap<>();
        publicProfile.put("bio", "liveBio");
        publicProfile.put("location", "liveLoc");
        publicProfile.put("website", "liveWeb");
        publicProfile.put("avatarUrl", "liveAvatar");
        Map<String, Object> md = new LinkedHashMap<>();
        md.put("profile", publicProfile);
        u.setMetadata(md);
        when(usersRepository.findById(1L)).thenReturn(Optional.of(u));
        when(reportsRepository.findByTargetTypeAndTargetId(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("username", "snapName");
        snap.put("bio", "snapBio");
        snap.put("location", "snapLoc");
        snap.put("website", "snapWeb");
        snap.put("avatarUrl", "snapAvatar");

        ModerationActionsEntity a = new ModerationActionsEntity();
        a.setQueueId(99L);
        a.setReason("REPORT_SNAPSHOT");
        a.setSnapshot(Map.of("content_snapshot_id", "report:1", "target_snapshot", snap));
        a.setCreatedAt(LocalDateTime.now());

        when(moderationActionsRepository.findAllByQueueId(99L)).thenReturn(List.of(a));
        when(reportsRepository.findByTargetTypeAndTargetId(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        AdminModerationLlmContextBuilder builder = newBuilder(
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

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(99L);
        req.setReviewStage("reported");

        PromptVars vars = builder.resolvePromptVarsSafe(req);
        assertNotNull(vars);
        assertTrue(vars.text().contains("bio: snapBio"));

        QueueCtx ctx = new QueueCtx(q, new ModerationPolicyConfigEntity(), "v1", Map.of());
        String json = builder.buildTextAuditInputJson(req, vars, ctx);
        assertNotNull(json);

        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(json);
        JsonNode pf = root.get("profile_fields");
        assertNotNull(pf);
        assertEquals("snapName", pf.get("username").asText());
        assertEquals("snapBio", pf.get("bio").asText());
        assertEquals("snapLoc", pf.get("location").asText());
        assertEquals("snapWeb", pf.get("website").asText());
        assertEquals("snapAvatar", pf.get("avatarUrl").asText());
    }
}
