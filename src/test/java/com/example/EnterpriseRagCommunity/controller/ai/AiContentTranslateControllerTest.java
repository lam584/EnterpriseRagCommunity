package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiSemanticTranslateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiContentTranslateControllerTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void translatePost_authNull_throwsAuthenticationException() {
        AiSemanticTranslateService svc = mock(AiSemanticTranslateService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiContentTranslateController c = new AiContentTranslateController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(null);

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.translatePost(1L, "en")
        );
    }

    @Test
    void translatePost_notAuthenticated_throwsAuthenticationException() {
        AiSemanticTranslateService svc = mock(AiSemanticTranslateService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiContentTranslateController c = new AiContentTranslateController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A")
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.translatePost(1L, "en")
        );
    }

    @Test
    void translatePost_anonymousPrincipal_throwsAuthenticationException() {
        AiSemanticTranslateService svc = mock(AiSemanticTranslateService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiContentTranslateController c = new AiContentTranslateController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "anonymousUser",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.translatePost(1L, "en")
        );
    }

    @Test
    void translatePost_userNotFound_throwsIllegalArgumentException() {
        AiSemanticTranslateService svc = mock(AiSemanticTranslateService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiContentTranslateController c = new AiContentTranslateController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.empty());

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> c.translatePost(1L, "en")
        );
    }

    @Test
    void translateComment_ok_returnsEmitterFromService() {
        AiSemanticTranslateService svc = mock(AiSemanticTranslateService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiContentTranslateController c = new AiContentTranslateController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
        UsersEntity u = new UsersEntity();
        u.setId(10L);
        u.setEmail("alice@example.com");
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(u));

        SseEmitter emitter = new SseEmitter();
        when(svc.translateCommentStream(eq(2L), eq("en"), eq(10L))).thenReturn(emitter);

        Assertions.assertSame(emitter, c.translateComment(2L, "en"));
    }
}

