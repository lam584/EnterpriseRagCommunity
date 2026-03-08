package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.QaSessionDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.QaHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QaHistoryControllerTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listSessions_authNull_throwsAuthenticationException() {
        QaHistoryService svc = mock(QaHistoryService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        QaHistoryController c = new QaHistoryController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(null);

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.listSessions(0, 20)
        );
    }

    @Test
    void listSessions_notAuthenticated_throwsAuthenticationException() {
        QaHistoryService svc = mock(QaHistoryService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        QaHistoryController c = new QaHistoryController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A")
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.listSessions(0, 20)
        );
    }

    @Test
    void listSessions_anonymousPrincipal_throwsAuthenticationException() {
        QaHistoryService svc = mock(QaHistoryService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        QaHistoryController c = new QaHistoryController(svc, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "anonymousUser",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.listSessions(0, 20)
        );
    }

    @Test
    void listSessions_userNotFound_throwsIllegalArgumentException() {
        QaHistoryService svc = mock(QaHistoryService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        QaHistoryController c = new QaHistoryController(svc, administratorService);

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
                () -> c.listSessions(0, 20)
        );
    }

    @Test
    void listSessions_ok_returnsServiceValue() {
        QaHistoryService svc = mock(QaHistoryService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        QaHistoryController c = new QaHistoryController(svc, administratorService);

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

        Page<QaSessionDTO> page = new PageImpl<>(List.of(new QaSessionDTO()));
        when(svc.listMySessions(eq(10L), eq(0), eq(20))).thenReturn(page);

        Assertions.assertSame(page, c.listSessions(0, 20));
    }
}

