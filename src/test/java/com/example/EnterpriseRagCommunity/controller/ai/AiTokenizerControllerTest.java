package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest;
import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.OpenSearchTokenizeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiTokenizerControllerTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tokenize_authNull_throwsAuthenticationException() {
        OpenSearchTokenizeService svc = mock(OpenSearchTokenizeService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiTokenizerController c = new AiTokenizerController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(null);

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.tokenize(new OpenSearchTokenizeRequest())
        );
    }

    @Test
    void tokenize_notAuthenticated_throwsAuthenticationException() {
        OpenSearchTokenizeService svc = mock(OpenSearchTokenizeService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiTokenizerController c = new AiTokenizerController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A")
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.tokenize(new OpenSearchTokenizeRequest())
        );
    }

    @Test
    void tokenize_anonymousPrincipal_throwsAuthenticationException() {
        OpenSearchTokenizeService svc = mock(OpenSearchTokenizeService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiTokenizerController c = new AiTokenizerController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "anonymousUser",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.tokenize(new OpenSearchTokenizeRequest())
        );
    }

    @Test
    void tokenize_userNotFound_throwsIllegalArgumentException() {
        OpenSearchTokenizeService svc = mock(OpenSearchTokenizeService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiTokenizerController c = new AiTokenizerController(svc, administratorService);

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
                () -> c.tokenize(new OpenSearchTokenizeRequest())
        );
    }

    @Test
    void tokenize_ok_returnsServiceValue() {
        OpenSearchTokenizeService svc = mock(OpenSearchTokenizeService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiTokenizerController c = new AiTokenizerController(svc, administratorService);

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

        OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
        OpenSearchTokenizeResponse resp = new OpenSearchTokenizeResponse();
        when(svc.tokenize(eq(req))).thenReturn(resp);

        Assertions.assertSame(resp, c.tokenize(req));
    }
}

