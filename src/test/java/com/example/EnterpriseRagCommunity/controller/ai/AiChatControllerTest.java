package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatResponseDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiChatService;
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

class AiChatControllerTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chat_authNull_throwsAuthenticationException() {
        AiChatService chatService = mock(AiChatService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiChatController c = new AiChatController(chatService, administratorService);

        SecurityContextHolder.getContext().setAuthentication(null);

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.chat(new AiChatStreamRequest())
        );
    }

    @Test
    void chat_notAuthenticated_throwsAuthenticationException() {
        AiChatService chatService = mock(AiChatService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiChatController c = new AiChatController(chatService, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A")
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.chat(new AiChatStreamRequest())
        );
    }

    @Test
    void chat_anonymousPrincipal_throwsAuthenticationException() {
        AiChatService chatService = mock(AiChatService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiChatController c = new AiChatController(chatService, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "anonymousUser",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.chat(new AiChatStreamRequest())
        );
    }

    @Test
    void chat_userNotFound_throwsIllegalArgumentException() {
        AiChatService chatService = mock(AiChatService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiChatController c = new AiChatController(chatService, administratorService);

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
                () -> c.chat(new AiChatStreamRequest())
        );
    }

    @Test
    void chat_ok_callsService() {
        AiChatService chatService = mock(AiChatService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiChatController c = new AiChatController(chatService, administratorService);

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

        AiChatResponseDTO resp = new AiChatResponseDTO();
        AiChatStreamRequest req = new AiChatStreamRequest();
        when(chatService.chatOnce(eq(req), eq(10L))).thenReturn(resp);

        Assertions.assertSame(resp, c.chat(req).getBody());
    }

    @Test
    void stream_ok_callsService() throws Exception {
        AiChatService chatService = mock(AiChatService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiChatController c = new AiChatController(chatService, administratorService);

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

        AiChatStreamRequest req = new AiChatStreamRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        c.stream(req, response);
        verify(chatService).streamChat(eq(req), eq(10L), eq(response));
    }
}

