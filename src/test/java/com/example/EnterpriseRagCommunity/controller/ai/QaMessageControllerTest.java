package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiChatService;
import com.example.EnterpriseRagCommunity.service.ai.QaMessageService;
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

class QaMessageControllerTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void toggleFavorite_authNull_throwsAuthenticationException() {
        QaMessageService qaMessageService = mock(QaMessageService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        QaMessageController c = new QaMessageController(qaMessageService, aiChatService, administratorService);

        SecurityContextHolder.getContext().setAuthentication(null);

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.toggleFavorite(1L)
        );
    }

    @Test
    void toggleFavorite_notAuthenticated_throwsAuthenticationException() {
        QaMessageService qaMessageService = mock(QaMessageService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        QaMessageController c = new QaMessageController(qaMessageService, aiChatService, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A")
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.toggleFavorite(1L)
        );
    }

    @Test
    void toggleFavorite_anonymousPrincipal_throwsAuthenticationException() {
        QaMessageService qaMessageService = mock(QaMessageService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        QaMessageController c = new QaMessageController(qaMessageService, aiChatService, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "anonymousUser",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.toggleFavorite(1L)
        );
    }

    @Test
    void toggleFavorite_userNotFound_throwsIllegalArgumentException() {
        QaMessageService qaMessageService = mock(QaMessageService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        QaMessageController c = new QaMessageController(qaMessageService, aiChatService, administratorService);

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
                () -> c.toggleFavorite(1L)
        );
    }

    @Test
    void toggleFavorite_ok_returnsServiceValue() {
        QaMessageService qaMessageService = mock(QaMessageService.class);
        AiChatService aiChatService = mock(AiChatService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        QaMessageController c = new QaMessageController(qaMessageService, aiChatService, administratorService);

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
        when(qaMessageService.toggleMyMessageFavorite(eq(10L), eq(1L))).thenReturn(true);

        Assertions.assertEquals(true, c.toggleFavorite(1L).getBody());
    }
}

