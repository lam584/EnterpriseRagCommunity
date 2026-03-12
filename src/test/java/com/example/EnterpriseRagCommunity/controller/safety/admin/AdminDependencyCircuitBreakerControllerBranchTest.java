package com.example.EnterpriseRagCommunity.controller.safety.admin;

import com.example.EnterpriseRagCommunity.dto.safety.DependencyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.dto.safety.DependencyCircuitBreakerUpdateRequest;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDependencyCircuitBreakerControllerBranchTest {

    @Mock
    private AppSettingsService appSettingsService;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private AuditDiffBuilder auditDiffBuilder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_shouldNormalizeDependencyAndReturnConfig() {
        AdminDependencyCircuitBreakerController controller =
                new AdminDependencyCircuitBreakerController(appSettingsService, auditLogWriter, auditDiffBuilder);

        when(appSettingsService.getLongOrDefault("deps.MYSQL.failureThreshold", 5)).thenReturn(7L);
        when(appSettingsService.getLongOrDefault("deps.MYSQL.cooldownSeconds", 30)).thenReturn(40L);

        DependencyCircuitBreakerConfigDTO out = controller.get(" mysql ");
        assertEquals("MYSQL", out.getDependency());
        assertEquals(7, out.getFailureThreshold());
        assertEquals(40, out.getCooldownSeconds());
    }

    @Test
    void normalizeDep_shouldThrowWhenNullOrBlank() {
        ResponseStatusException ex1 = assertThrows(
                ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(AdminDependencyCircuitBreakerController.class, "normalizeDep", (Object) null)
        );
        assertEquals(400, ex1.getStatusCode().value());
        assertEquals("dependency 不能为空", ex1.getReason());

        ResponseStatusException ex2 = assertThrows(
                ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(AdminDependencyCircuitBreakerController.class, "normalizeDep", "   ")
        );
        assertEquals(400, ex2.getStatusCode().value());
        assertEquals("dependency 不能为空", ex2.getReason());
    }

    @Test
    void update_shouldThrowBadRequestWhenConfigNull() {
        AdminDependencyCircuitBreakerController controller =
                new AdminDependencyCircuitBreakerController(appSettingsService, auditLogWriter, auditDiffBuilder);

        DependencyCircuitBreakerUpdateRequest req = new DependencyCircuitBreakerUpdateRequest();
        req.setReason("r");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.update("mysql", req, null)
        );
        assertEquals(400, ex.getStatusCode().value());
        assertEquals("config 不能为空", ex.getReason());
    }

    @Test
    void update_shouldClampValuesAndWriteAudit_withSessionUserId() {
        AdminDependencyCircuitBreakerController controller =
                new AdminDependencyCircuitBreakerController(appSettingsService, auditLogWriter, auditDiffBuilder);
        SecurityContextHolder.getContext().setAuthentication(authenticated("admin@example.com"));

        when(appSettingsService.getLongOrDefault("deps.ES.failureThreshold", 5)).thenReturn(3L);
        when(appSettingsService.getLongOrDefault("deps.ES.cooldownSeconds", 30)).thenReturn(25L);
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of("changed", true));

        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("auth.userId")).thenReturn(12L);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(session);

        DependencyCircuitBreakerConfigDTO cfg = new DependencyCircuitBreakerConfigDTO();
        cfg.setFailureThreshold(-3);
        cfg.setCooldownSeconds(5000);
        DependencyCircuitBreakerUpdateRequest req = new DependencyCircuitBreakerUpdateRequest();
        req.setConfig(cfg);
        req.setReason("test");

        DependencyCircuitBreakerConfigDTO out = controller.update("es", req, request);
        assertEquals("ES", out.getDependency());
        assertEquals(0, out.getFailureThreshold());
        assertEquals(3600, out.getCooldownSeconds());

        verify(appSettingsService).upsertString("deps.ES.failureThreshold", "0");
        verify(appSettingsService).upsertString("deps.ES.cooldownSeconds", "3600");
        verify(auditLogWriter).write(
                eq(12L),
                eq("admin@example.com"),
                eq("DEPENDENCY_CIRCUIT_BREAKER_UPDATE"),
                eq("APP_SETTINGS"),
                isNull(),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.SUCCESS),
                eq("更新依赖熔断配置"),
                isNull(),
                any()
        );
    }

    @Test
    void update_shouldUseDefaults_whenThresholdsAreNull() {
        AdminDependencyCircuitBreakerController controller =
                new AdminDependencyCircuitBreakerController(appSettingsService, auditLogWriter, auditDiffBuilder);
        SecurityContextHolder.getContext().setAuthentication(authenticated("admin@example.com"));

        when(appSettingsService.getLongOrDefault("deps.MYDEP.failureThreshold", 5)).thenReturn(5L);
        when(appSettingsService.getLongOrDefault("deps.MYDEP.cooldownSeconds", 30)).thenReturn(30L);
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        DependencyCircuitBreakerConfigDTO cfg = new DependencyCircuitBreakerConfigDTO();
        cfg.setFailureThreshold(null);
        cfg.setCooldownSeconds(null);
        DependencyCircuitBreakerUpdateRequest req = new DependencyCircuitBreakerUpdateRequest();
        req.setConfig(cfg);
        req.setReason("default");

        DependencyCircuitBreakerConfigDTO out = controller.update("mydep", req, null);
        assertEquals("MYDEP", out.getDependency());
        assertEquals(5, out.getFailureThreshold());
        assertEquals(30, out.getCooldownSeconds());
    }

    @Test
    void resolveActor_shouldThrowUnauthorized_forNullUnauthenticatedAndAnonymous() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        SecurityContextHolder.clearContext();
        ResponseStatusException ex1 = assertThrows(
                ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(AdminDependencyCircuitBreakerController.class, "resolveActor", request)
        );
        assertEquals(401, ex1.getStatusCode().value());

        Authentication unauthenticated = mock(Authentication.class);
        when(unauthenticated.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        ResponseStatusException ex2 = assertThrows(
                ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(AdminDependencyCircuitBreakerController.class, "resolveActor", request)
        );
        assertEquals(401, ex2.getStatusCode().value());

        Authentication anonymous = mock(Authentication.class);
        when(anonymous.isAuthenticated()).thenReturn(true);
        when(anonymous.getPrincipal()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(anonymous);
        ResponseStatusException ex3 = assertThrows(
                ResponseStatusException.class,
                () -> ReflectionTestUtils.invokeMethod(AdminDependencyCircuitBreakerController.class, "resolveActor", request)
        );
        assertEquals(401, ex3.getStatusCode().value());
    }

    @Test
    void resolveActor_shouldIgnoreNonNumberSessionUserId() {
        SecurityContextHolder.getContext().setAuthentication(authenticated("admin@example.com"));
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("auth.userId")).thenReturn("not-number");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(session);

        Object actor = ReflectionTestUtils.invokeMethod(AdminDependencyCircuitBreakerController.class, "resolveActor", request);
        assertEquals("admin@example.com", ReflectionTestUtils.getField(actor, "name"));
        assertNull(ReflectionTestUtils.getField(actor, "userId"));
    }

    @Test
    void resolveActor_shouldHandleRequestWithoutSession() {
        SecurityContextHolder.getContext().setAuthentication(authenticated("admin@example.com"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        Object actor = ReflectionTestUtils.invokeMethod(AdminDependencyCircuitBreakerController.class, "resolveActor", request);
        assertEquals("admin@example.com", ReflectionTestUtils.getField(actor, "name"));
        assertNull(ReflectionTestUtils.getField(actor, "userId"));
    }

    @Test
    void update_shouldSwallowAuditException() {
        AdminDependencyCircuitBreakerController controller =
                new AdminDependencyCircuitBreakerController(appSettingsService, auditLogWriter, auditDiffBuilder);
        SecurityContextHolder.getContext().setAuthentication(authenticated("admin@example.com"));

        when(appSettingsService.getLongOrDefault("deps.API.failureThreshold", 5)).thenReturn(1L);
        when(appSettingsService.getLongOrDefault("deps.API.cooldownSeconds", 30)).thenReturn(2L);
        when(auditDiffBuilder.build(any(), any())).thenThrow(new RuntimeException("boom"));

        DependencyCircuitBreakerConfigDTO cfg = new DependencyCircuitBreakerConfigDTO();
        cfg.setFailureThreshold(10);
        cfg.setCooldownSeconds(20);
        DependencyCircuitBreakerUpdateRequest req = new DependencyCircuitBreakerUpdateRequest();
        req.setConfig(cfg);
        req.setReason("r");

        DependencyCircuitBreakerConfigDTO out = controller.update("api", req, null);
        assertEquals("API", out.getDependency());
        assertEquals(10, out.getFailureThreshold());
        assertEquals(20, out.getCooldownSeconds());
    }

    private static Authentication authenticated(String username) {
        TestingAuthenticationToken token = new TestingAuthenticationToken(username, "n/a", "ROLE_ADMIN");
        token.setAuthenticated(true);
        return token;
    }
}
