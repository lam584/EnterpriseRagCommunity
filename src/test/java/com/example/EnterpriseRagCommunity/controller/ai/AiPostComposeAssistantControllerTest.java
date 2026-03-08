package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostComposeStreamRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiPostComposeAssistantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPostComposeAssistantControllerTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stream_authNull_throwsAuthenticationException() {
        AiPostComposeAssistantService svc = mock(AiPostComposeAssistantService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostComposeAssistantController c = new AiPostComposeAssistantController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(null);

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.stream(new AiPostComposeStreamRequest(), new MockHttpServletResponse())
        );
    }

    @Test
    void stream_notAuthenticated_throwsAuthenticationException() {
        AiPostComposeAssistantService svc = mock(AiPostComposeAssistantService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostComposeAssistantController c = new AiPostComposeAssistantController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A")
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.stream(new AiPostComposeStreamRequest(), new MockHttpServletResponse())
        );
    }

    @Test
    void stream_anonymousPrincipal_throwsAuthenticationException() {
        AiPostComposeAssistantService svc = mock(AiPostComposeAssistantService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostComposeAssistantController c = new AiPostComposeAssistantController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "anonymousUser",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.stream(new AiPostComposeStreamRequest(), new MockHttpServletResponse())
        );
    }

    @Test
    void stream_userNotFound_throwsIllegalArgumentException() {
        AiPostComposeAssistantService svc = mock(AiPostComposeAssistantService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostComposeAssistantController c = new AiPostComposeAssistantController(svc, administratorService);

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
                () -> c.stream(new AiPostComposeStreamRequest(), new MockHttpServletResponse())
        );
    }

    @Test
    void stream_ok_callsService() throws Exception {
        AiPostComposeAssistantService svc = mock(AiPostComposeAssistantService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiPostComposeAssistantController c = new AiPostComposeAssistantController(svc, administratorService);

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

        AiPostComposeStreamRequest req = new AiPostComposeStreamRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        c.stream(req, resp);
        verify(svc).streamComposeEdit(eq(req), eq(10L), eq(resp));
    }
}

