package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.UpdateAssistantPreferencesRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.PortalChatConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AiAssistantPreferencesControllerAuditTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        UsersRepository usersRepository = mock(UsersRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        u.setEmail("alice@example.com");
        when(usersRepository.findByEmailAndIsDeletedFalse("alice@example.com")).thenReturn(Optional.of(u));
        when(usersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AiAssistantPreferencesController c = new AiAssistantPreferencesController(
            usersRepository,
            auditLogWriter,
            auditDiffBuilder,
            new DefaultListableBeanFactory().getBeanProvider(PortalChatConfigService.class)
        );
        UpdateAssistantPreferencesRequest req = new UpdateAssistantPreferencesRequest();
        req.setDefaultModel("m1");

        ResponseEntity<?> resp = c.updatePreferences(req);
        org.junit.jupiter.api.Assertions.assertEquals(200, resp.getStatusCode().value());

        verify(auditLogWriter).write(
                eq(10L),
                eq("alice@example.com"),
                eq("ASSISTANT_PREFERENCES_UPDATE"),
                eq("USER"),
                eq(10L),
                any(),
                any(),
                eq(null),
                any()
        );
    }
}

