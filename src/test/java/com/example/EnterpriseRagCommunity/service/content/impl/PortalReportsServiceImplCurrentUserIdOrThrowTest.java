package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.testsupport.SecurityContextTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortalReportsServiceImplCurrentUserIdOrThrowTest {

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
        SecurityContextTestSupport.clear();
    }

    @Test
    void currentUserIdOrThrow_authNull_throwsAuthenticationException() {
        PortalReportsServiceImpl svc = newService();
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow"));
    }

    @Test
    void currentUserIdOrThrow_notAuthenticated_throwsAuthenticationException() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        when(auth.getPrincipal()).thenReturn("u@example.com");
        when(auth.getName()).thenReturn("u@example.com");
        SecurityContextHolder.getContext().setAuthentication(auth);

        PortalReportsServiceImpl svc = newService();
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow"));
    }

    @Test
    void currentUserIdOrThrow_anonymousUser_throwsAuthenticationException() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("anonymousUser");
        when(auth.getName()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(auth);

        PortalReportsServiceImpl svc = newService();
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow"));
    }

    @Test
    void currentUserIdOrThrow_adminUserMissing_throwsIllegalArgumentException() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("u@example.com");
        when(auth.getName()).thenReturn("u@example.com");
        SecurityContextHolder.getContext().setAuthentication(auth);

        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty());

        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);

        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow"));
    }

    @Test
    void currentUserIdOrThrow_success_returnsId() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("u@example.com");
        when(auth.getName()).thenReturn("u@example.com");
        SecurityContextHolder.getContext().setAuthentication(auth);

        AdministratorService administratorService = mock(AdministratorService.class);
        UsersEntity u = new UsersEntity();
        u.setId(123L);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(u));

        PortalReportsServiceImpl svc = newService();
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);

        Long id = ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow");
        assertEquals(123L, id);
    }
}
