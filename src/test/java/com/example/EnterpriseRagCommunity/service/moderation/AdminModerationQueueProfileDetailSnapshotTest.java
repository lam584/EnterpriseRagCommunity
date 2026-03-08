package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.controller.AccountProfileController;
import com.example.EnterpriseRagCommunity.dto.access.UpdateMyProfileRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class AdminModerationQueueProfileDetailSnapshotTest {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ModerationQueueRepository moderationQueueRepository;

    @Autowired
    private ModerationActionsRepository moderationActionsRepository;

    @Autowired
    private AdminModerationQueueService adminModerationQueueService;

    @Autowired
    private AccountProfileController accountProfileController;

    @AfterEach
    void cleanupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getDetail_shouldFallbackToSnapshotWhenProfilePendingCleared() {
        UsersEntity u = new UsersEntity();
        u.setEmail("profile_mod_detail_snapshot_test@example.com");
        u.setUsername("PublicName");
        u.setPasswordHash("x");
        u.setStatus(AccountStatus.ACTIVE);
        u.setIsDeleted(false);

        Map<String, Object> md = new LinkedHashMap<>();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("bio", "old bio");
        profile.put("location", "old loc");
        profile.put("website", "https://old.example.com");
        profile.put("avatarUrl", "https://old.example.com/a.png");
        md.put("profile", profile);
        u.setMetadata(md);

        UsersEntity saved = usersRepository.save(u);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(saved.getEmail(), "N/A", java.util.List.of()));

        UpdateMyProfileRequest req = new UpdateMyProfileRequest();
        req.setUsername("PendingName");
        req.setBio("new bio");
        req.setLocation("new loc");
        req.setWebsite("https://new.example.com");
        req.setAvatarUrl("https://new.example.com/a.png");

        var resp = accountProfileController.updateMyProfile(req);
        assertEquals(200, resp.getStatusCode().value());

        UsersEntity after = usersRepository.findById(saved.getId()).orElseThrow();
        assertNotNull(after.getMetadata());
        assertTrue(after.getMetadata().get("profilePending") instanceof Map);

        var qOpt = moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.PROFILE, after.getId());
        assertTrue(qOpt.isPresent());
        Long queueId = qOpt.get().getId();
        assertNotNull(queueId);

        assertTrue(moderationActionsRepository.findAllByQueueId(queueId).stream().anyMatch(a ->
                a != null && "PROFILE_PENDING_SNAPSHOT".equals(a.getReason())
        ));

        Map<String, Object> md2 = new LinkedHashMap<>(after.getMetadata());
        md2.remove("profilePending");
        md2.remove("profilePendingSubmittedAt");
        after.setMetadata(md2);
        usersRepository.save(after);

        var detail = adminModerationQueueService.getDetail(queueId);
        assertNotNull(detail);
        assertNotNull(detail.getProfile());
        assertEquals("PendingName", detail.getProfile().getPendingUsername());
        assertEquals("new bio", detail.getProfile().getPendingBio());
        assertEquals("new loc", detail.getProfile().getPendingLocation());
        assertEquals("https://new.example.com", detail.getProfile().getPendingWebsite());
        assertEquals("https://new.example.com/a.png", detail.getProfile().getPendingAvatarUrl());
        assertNotNull(detail.getProfile().getPendingSubmittedAt());
    }
}

