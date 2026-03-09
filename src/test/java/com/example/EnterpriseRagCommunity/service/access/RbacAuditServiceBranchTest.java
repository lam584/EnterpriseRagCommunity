package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.RbacAuditLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.RbacAuditLogsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RbacAuditServiceBranchTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void record_should_write_rbac_log_and_audit_log_with_request_context() {
        RbacAuditLogsRepository rbacRepo = mock(RbacAuditLogsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        RbacAuditService s = new RbacAuditService(rbacRepo, usersRepository, new ObjectMapper(), auditLogWriter);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@x.com", "n/a", java.util.List.of())
        );
        UsersEntity u = new UsersEntity();
        u.setId(11L);
        when(usersRepository.findByEmailAndIsDeletedFalse("admin@x.com")).thenReturn(Optional.of(u));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Admin-Reason", "reason");
        req.addHeader("X-Request-Id", "req-1");
        req.addHeader("User-Agent", "ua");
        req.addHeader("X-Forwarded-For", "3.3.3.3,4.4.4.4");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        ArgumentCaptor<RbacAuditLogsEntity> cap = ArgumentCaptor.forClass(RbacAuditLogsEntity.class);
        s.record("PERMISSION_UPDATE", "perm", "1", Map.of("before", 1), Map.of("after", 2));

        verify(rbacRepo).save(cap.capture());
        RbacAuditLogsEntity out = cap.getValue();
        assertEquals(11L, out.getActorUserId());
        assertEquals("PERMISSION_UPDATE", out.getAction());
        assertEquals("perm", out.getTargetType());
        assertEquals("1", out.getTargetId());
        assertEquals("reason", out.getReason());
        assertEquals("req-1", out.getRequestId());
        assertEquals("ua", out.getUserAgent());
        assertEquals("3.3.3.3", out.getRequestIp());
        assertNotNull(out.getCreatedAt());
        assertNotNull(out.getDiffJson());

        verify(auditLogWriter).write(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void record_should_handle_missing_auth_request_and_audit_writer_failure() {
        RbacAuditLogsRepository rbacRepo = mock(RbacAuditLogsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        doThrow(new RuntimeException("x")).when(auditLogWriter)
                .write(any(), any(), any(), any(), any(), any(), any(), any(), any());
        RbacAuditService s = new RbacAuditService(rbacRepo, usersRepository, new ObjectMapper(), auditLogWriter);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", "n/a", java.util.List.of())
        );

        s.record("RBAC_X", "t", "2", Map.of("k", "v"));
        verify(rbacRepo).save(any(RbacAuditLogsEntity.class));
    }
}
