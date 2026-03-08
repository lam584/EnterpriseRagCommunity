package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.NotificationsEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NotificationsControllerAuditTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void markReadWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        NotificationsService notificationsService = mock(NotificationsService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(u));
        when(notificationsService.markMyNotificationRead(1L)).thenReturn(new NotificationsEntity());

        NotificationsController c = new NotificationsController();
        ReflectionTestUtils.setField(c, "notificationsService", notificationsService);
        ReflectionTestUtils.setField(c, "administratorService", administratorService);
        ReflectionTestUtils.setField(c, "auditLogWriter", auditLogWriter);

        ResponseEntity<NotificationsEntity> resp = c.markRead(1L);
        org.junit.jupiter.api.Assertions.assertEquals(200, resp.getStatusCode().value());

        verify(auditLogWriter).write(
                eq(10L),
                eq("alice@example.com"),
                eq("NOTIFICATION_MARK_READ"),
                eq("NOTIFICATION"),
                eq(1L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void markReadBatchWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        NotificationsService notificationsService = mock(NotificationsService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(u));
        when(notificationsService.markMyNotificationsRead(List.of(1L, 2L))).thenReturn(2);

        NotificationsController c = new NotificationsController();
        ReflectionTestUtils.setField(c, "notificationsService", notificationsService);
        ReflectionTestUtils.setField(c, "administratorService", administratorService);
        ReflectionTestUtils.setField(c, "auditLogWriter", auditLogWriter);

        ResponseEntity<Map<String, Integer>> resp = c.markReadBatch(Map.of("ids", List.of(1, 2)));
        org.junit.jupiter.api.Assertions.assertEquals(200, resp.getStatusCode().value());

        verify(auditLogWriter).write(
                eq(10L),
                eq("alice@example.com"),
                eq("NOTIFICATION_MARK_READ_BATCH"),
                eq("NOTIFICATION"),
                eq(null),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void deleteWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        NotificationsService notificationsService = mock(NotificationsService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(u));

        NotificationsController c = new NotificationsController();
        ReflectionTestUtils.setField(c, "notificationsService", notificationsService);
        ReflectionTestUtils.setField(c, "administratorService", administratorService);
        ReflectionTestUtils.setField(c, "auditLogWriter", auditLogWriter);

        ResponseEntity<?> resp = c.delete(9L);
        org.junit.jupiter.api.Assertions.assertEquals(204, resp.getStatusCode().value());

        verify(auditLogWriter).write(
                eq(10L),
                eq("alice@example.com"),
                eq("NOTIFICATION_DELETE"),
                eq("NOTIFICATION"),
                eq(9L),
                any(),
                any(),
                eq(null),
                any()
        );
    }
}

