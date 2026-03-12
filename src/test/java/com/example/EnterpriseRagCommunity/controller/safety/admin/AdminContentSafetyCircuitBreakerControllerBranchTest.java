package com.example.EnterpriseRagCommunity.controller.safety.admin;

import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerStatusDTO;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerUpdateRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminContentSafetyCircuitBreakerControllerBranchTest {

    @Mock
    private ContentSafetyCircuitBreakerService circuitBreakerService;
    @Mock
    private AdministratorService administratorService;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private AuditDiffBuilder auditDiffBuilder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void status_and_config_shouldDelegateToService() {
        AdminContentSafetyCircuitBreakerController controller = new AdminContentSafetyCircuitBreakerController(
                circuitBreakerService, administratorService, auditLogWriter, auditDiffBuilder
        );
        ContentSafetyCircuitBreakerStatusDTO status = new ContentSafetyCircuitBreakerStatusDTO();
        ContentSafetyCircuitBreakerConfigDTO config = new ContentSafetyCircuitBreakerConfigDTO();
        when(circuitBreakerService.getStatus(50)).thenReturn(status);
        when(circuitBreakerService.getConfig()).thenReturn(config);

        assertSame(status, controller.status());
        assertSame(config, controller.config());
    }

    @Test
    void update_shouldWriteAuditSuccessfully_withSessionUserId() {
        AdminContentSafetyCircuitBreakerController controller = new AdminContentSafetyCircuitBreakerController(
                circuitBreakerService, administratorService, auditLogWriter, auditDiffBuilder
        );
        SecurityContextHolder.getContext().setAuthentication(authenticated("admin@example.com"));

        ContentSafetyCircuitBreakerConfigDTO before = new ContentSafetyCircuitBreakerConfigDTO();
        ContentSafetyCircuitBreakerConfigDTO afterCfg = new ContentSafetyCircuitBreakerConfigDTO();
        ContentSafetyCircuitBreakerStatusDTO out = new ContentSafetyCircuitBreakerStatusDTO();
        out.setConfig(afterCfg);
        when(circuitBreakerService.getConfig()).thenReturn(before);
        when(circuitBreakerService.update(any(), eq(9L), eq("admin@example.com"), eq("变更"))).thenReturn(out);
        when(auditDiffBuilder.build(before, afterCfg)).thenReturn(Map.of("changed", true));

        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("auth.userId")).thenReturn(9L);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(session);

        ContentSafetyCircuitBreakerUpdateRequest req = new ContentSafetyCircuitBreakerUpdateRequest();
        req.setConfig(afterCfg);
        req.setReason("变更");

        ContentSafetyCircuitBreakerStatusDTO actual = controller.update(req, request);
        assertSame(out, actual);
        verify(auditLogWriter).write(
                eq(9L),
                eq("admin@example.com"),
                eq("CONTENT_SAFETY_CIRCUIT_BREAKER_UPDATE"),
                eq("APP_SETTINGS"),
                isNull(),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.SUCCESS),
                eq("更新内容安全熔断配置"),
                isNull(),
                any()
        );
    }

    @Test
    void update_shouldFallbackToAdminService_whenNoSessionUserId() {
        AdminContentSafetyCircuitBreakerController controller = new AdminContentSafetyCircuitBreakerController(
                circuitBreakerService, administratorService, auditLogWriter, auditDiffBuilder
        );
        SecurityContextHolder.getContext().setAuthentication(authenticated("admin@example.com"));

        UsersEntity u = new UsersEntity();
        u.setId(77L);
        when(administratorService.findByUsername("admin@example.com")).thenReturn(Optional.of(u));

        ContentSafetyCircuitBreakerConfigDTO before = new ContentSafetyCircuitBreakerConfigDTO();
        ContentSafetyCircuitBreakerConfigDTO cfg = new ContentSafetyCircuitBreakerConfigDTO();
        ContentSafetyCircuitBreakerStatusDTO out = new ContentSafetyCircuitBreakerStatusDTO();
        out.setConfig(cfg);
        when(circuitBreakerService.getConfig()).thenReturn(before);
        when(circuitBreakerService.update(any(), eq(77L), eq("admin@example.com"), eq("r"))).thenReturn(out);
        when(auditDiffBuilder.build(before, cfg)).thenReturn(Map.of());

        ContentSafetyCircuitBreakerUpdateRequest req = new ContentSafetyCircuitBreakerUpdateRequest();
        req.setConfig(cfg);
        req.setReason("r");

        ContentSafetyCircuitBreakerStatusDTO actual = controller.update(req, null);
        assertSame(out, actual);
    }

    @Test
    void update_shouldUseSystemName_whenEmailBlank() {
        AdminContentSafetyCircuitBreakerController controller = new AdminContentSafetyCircuitBreakerController(
                circuitBreakerService, administratorService, auditLogWriter, auditDiffBuilder
        );
        Authentication blankName = mock(Authentication.class);
        when(blankName.isAuthenticated()).thenReturn(true);
        when(blankName.getPrincipal()).thenReturn("user");
        when(blankName.getName()).thenReturn("   ");
        SecurityContextHolder.getContext().setAuthentication(blankName);
        when(administratorService.findByUsername("   ")).thenReturn(Optional.empty());

        ContentSafetyCircuitBreakerConfigDTO before = new ContentSafetyCircuitBreakerConfigDTO();
        ContentSafetyCircuitBreakerConfigDTO cfg = new ContentSafetyCircuitBreakerConfigDTO();
        ContentSafetyCircuitBreakerStatusDTO out = new ContentSafetyCircuitBreakerStatusDTO();
        out.setConfig(cfg);
        when(circuitBreakerService.getConfig()).thenReturn(before);
        when(circuitBreakerService.update(any(), isNull(), eq("SYSTEM"), eq("r"))).thenReturn(out);
        when(auditDiffBuilder.build(before, cfg)).thenReturn(Map.of());

        ContentSafetyCircuitBreakerUpdateRequest req = new ContentSafetyCircuitBreakerUpdateRequest();
        req.setConfig(cfg);
        req.setReason("r");
        controller.update(req, null);

        verify(auditLogWriter).write(
                isNull(),
                eq("SYSTEM"),
                eq("CONTENT_SAFETY_CIRCUIT_BREAKER_UPDATE"),
                eq("APP_SETTINGS"),
                isNull(),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.SUCCESS),
                eq("更新内容安全熔断配置"),
                isNull(),
                any()
        );
    }

    @Test
    void update_shouldAddEvent_whenAuditWriteFails() {
        AdminContentSafetyCircuitBreakerController controller = new AdminContentSafetyCircuitBreakerController(
                circuitBreakerService, administratorService, auditLogWriter, auditDiffBuilder
        );
        SecurityContextHolder.getContext().setAuthentication(authenticated("admin@example.com"));
        when(administratorService.findByUsername("admin@example.com")).thenReturn(Optional.empty());

        ContentSafetyCircuitBreakerConfigDTO before = new ContentSafetyCircuitBreakerConfigDTO();
        ContentSafetyCircuitBreakerConfigDTO cfg = new ContentSafetyCircuitBreakerConfigDTO();
        ContentSafetyCircuitBreakerStatusDTO out = new ContentSafetyCircuitBreakerStatusDTO();
        out.setConfig(cfg);
        when(circuitBreakerService.getConfig()).thenReturn(before);
        when(circuitBreakerService.update(any(), isNull(), eq("admin@example.com"), eq("r"))).thenReturn(out);
        when(auditDiffBuilder.build(before, cfg)).thenThrow(new RuntimeException("boom"));

        ContentSafetyCircuitBreakerUpdateRequest req = new ContentSafetyCircuitBreakerUpdateRequest();
        req.setConfig(cfg);
        req.setReason("r");

        ContentSafetyCircuitBreakerStatusDTO actual = controller.update(req, null);
        assertSame(out, actual);
        verify(circuitBreakerService).addEvent(
                eq("AUDIT_WRITE_FAIL"),
                eq("审计写入失败（已降级为仅内存态记录）"),
                eq(Map.of())
        );
    }

    @Test
    void resolveActor_shouldThrowUnauthorized_forNullUnauthenticatedAndAnonymous() {
        AdminContentSafetyCircuitBreakerController controller = controller();
        HttpServletRequest request = mock(HttpServletRequest.class);

        SecurityContextHolder.clearContext();
        ResponseStatusException ex1 = assertThrows(
                ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(controller, "resolveActor", request)
        );
        assertEquals(401, ex1.getStatusCode().value());

        Authentication unauthenticated = mock(Authentication.class);
        when(unauthenticated.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        ResponseStatusException ex2 = assertThrows(
                ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(controller, "resolveActor", request)
        );
        assertEquals(401, ex2.getStatusCode().value());

        Authentication anonymous = mock(Authentication.class);
        when(anonymous.isAuthenticated()).thenReturn(true);
        when(anonymous.getPrincipal()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(anonymous);
        ResponseStatusException ex3 = assertThrows(
                ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(controller, "resolveActor", request)
        );
        assertEquals(401, ex3.getStatusCode().value());
    }

    @Test
    void resolveActor_shouldIgnoreNonNumberSessionIdAndFallbackEmptyUser() {
        SecurityContextHolder.getContext().setAuthentication(authenticated("admin@example.com"));
        when(administratorService.findByUsername("admin@example.com")).thenReturn(Optional.empty());

        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("auth.userId")).thenReturn("x");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(session);

        Object actor = ReflectionTestUtils.invokeMethod(controller(), "resolveActor", request);
        assertEquals("admin@example.com", ReflectionTestUtils.getField(actor, "name"));
        assertNull(ReflectionTestUtils.getField(actor, "userId"));
    }

    @Test
    void resolveActor_shouldUseSystemWhenEmailIsNull_andRequestHasNoSession() {
        Authentication nullName = mock(Authentication.class);
        when(nullName.isAuthenticated()).thenReturn(true);
        when(nullName.getPrincipal()).thenReturn("user");
        when(nullName.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(nullName);
        when(administratorService.findByUsername(null)).thenReturn(Optional.empty());

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        Object actor = ReflectionTestUtils.invokeMethod(controller(), "resolveActor", request);
        assertEquals("SYSTEM", ReflectionTestUtils.getField(actor, "name"));
        assertNull(ReflectionTestUtils.getField(actor, "userId"));
    }

    private AdminContentSafetyCircuitBreakerController controller() {
        return new AdminContentSafetyCircuitBreakerController(
                circuitBreakerService, administratorService, auditLogWriter, auditDiffBuilder
        );
    }

    private static Authentication authenticated(String username) {
        TestingAuthenticationToken token = new TestingAuthenticationToken(username, "n/a", "ROLE_ADMIN");
        token.setAuthenticated(true);
        return token;
    }
}
