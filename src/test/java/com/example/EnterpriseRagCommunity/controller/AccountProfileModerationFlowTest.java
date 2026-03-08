package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.controller.portal.PortalUsersController;
import com.example.EnterpriseRagCommunity.dto.access.UpdateMyProfileRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ActionType;
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
public class AccountProfileModerationFlowTest {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ModerationQueueRepository moderationQueueRepository;

    @Autowired
    private ModerationActionsRepository moderationActionsRepository;

    @Autowired
    private AccountProfileController accountProfileController;

    @Autowired
    private PortalUsersController portalUsersController;

    @AfterEach
    void cleanupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateProfile_shouldWritePending_andNotAffectPublicProfile() {
        UsersEntity u = new UsersEntity();
        u.setEmail("profile_mod_test@example.com");
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
        req.setBioPresent(true);
        req.setBio("new bio");

        var resp = accountProfileController.updateMyProfile(req);
        assertEquals(200, resp.getStatusCode().value());

        UsersEntity after = usersRepository.findById(saved.getId()).orElseThrow();
        assertEquals("PublicName", after.getUsername());

        Map<String, Object> mdAfter = after.getMetadata();
        assertNotNull(mdAfter);
        assertTrue(mdAfter.get("profile") instanceof Map);
        assertTrue(mdAfter.get("profilePending") instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> publicProfile = (Map<String, Object>) mdAfter.get("profile");
        @SuppressWarnings("unchecked")
        Map<String, Object> pendingProfile = (Map<String, Object>) mdAfter.get("profilePending");

        assertEquals("old bio", publicProfile.get("bio"));
        assertEquals("new bio", pendingProfile.get("bio"));
        assertEquals("PendingName", pendingProfile.get("username"));

        var qOpt = moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.PROFILE, after.getId());
        assertTrue(qOpt.isPresent());
        Long queueId = qOpt.get().getId();
        assertNotNull(queueId);

        var actions = moderationActionsRepository.findAllByQueueId(queueId);
        assertNotNull(actions);
        assertTrue(actions.stream().anyMatch(a ->
                a != null
                        && a.getAction() == ActionType.NOTE
                        && "PROFILE_PENDING_SNAPSHOT".equals(a.getReason())
                        && a.getSnapshot() != null
                        && a.getSnapshot().get("pending_profile") instanceof Map
        ));

        var publicResp = portalUsersController.getPublicProfile(after.getId());
        assertEquals(200, publicResp.getStatusCode().value());
        assertNotNull(publicResp.getBody());
        assertTrue(publicResp.getBody() instanceof com.example.EnterpriseRagCommunity.dto.portal.PublicUserProfileDTO);
        com.example.EnterpriseRagCommunity.dto.portal.PublicUserProfileDTO publicDto =
                (com.example.EnterpriseRagCommunity.dto.portal.PublicUserProfileDTO) publicResp.getBody();
        assertEquals("PublicName", publicDto.getUsername());
    }
}
