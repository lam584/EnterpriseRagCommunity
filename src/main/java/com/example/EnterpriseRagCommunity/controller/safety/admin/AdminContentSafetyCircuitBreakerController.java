package com.example.EnterpriseRagCommunity.controller.safety.admin;

import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerStatusDTO;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerUpdateRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/safety/circuit-breaker")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminContentSafetyCircuitBreakerController {

    private final ContentSafetyCircuitBreakerService circuitBreakerService;
    private final AdministratorService administratorService;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @GetMapping("/status")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_safety_circuit_breaker','read'))")
    public ContentSafetyCircuitBreakerStatusDTO status() {
        return circuitBreakerService.getStatus(50);
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_safety_circuit_breaker','read'))")
    public ContentSafetyCircuitBreakerConfigDTO config() {
        return circuitBreakerService.getConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_safety_circuit_breaker','write'))")
    public ContentSafetyCircuitBreakerStatusDTO update(@Valid @RequestBody ContentSafetyCircuitBreakerUpdateRequest req, HttpServletRequest request) {
        Actor actor = resolveActor(request);
        ContentSafetyCircuitBreakerConfigDTO before = circuitBreakerService.getConfig();
        ContentSafetyCircuitBreakerStatusDTO out = circuitBreakerService.update(req.getConfig(), actor.userId, actor.name, req.getReason());
        try {
            Map<String, Object> details = new java.util.LinkedHashMap<>(auditDiffBuilder.build(before, out.getConfig()));
            details.put("key", ContentSafetyCircuitBreakerService.KEY_CONFIG_JSON);
            details.put("reason", req.getReason());
            auditLogWriter.write(
                    actor.userId,
                    actor.name,
                    "CONTENT_SAFETY_CIRCUIT_BREAKER_UPDATE",
                    "APP_SETTINGS",
                    null,
                    AuditResult.SUCCESS,
                    "更新内容安全熔断配置",
                    null,
                    details
            );
        } catch (Exception ignored) {
            circuitBreakerService.addEvent("AUDIT_WRITE_FAIL", "审计写入失败（已降级为仅内存态记录）", Map.of());
        }
        return out;
    }

    private Actor resolveActor(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或会话已过期");
        }
        String email = auth.getName();
        Long userId = null;
        if (request != null && request.getSession(false) != null) {
            Object v = request.getSession(false).getAttribute("auth.userId");
            if (v instanceof Number n) userId = n.longValue();
        }
        if (userId == null) {
            UsersEntity u = administratorService.findByUsername(email).orElse(null);
            userId = u == null ? null : u.getId();
        }
        Actor a = new Actor();
        a.userId = userId;
        a.name = email == null || email.isBlank() ? "SYSTEM" : email;
        return a;
    }

    private static final class Actor {
        private Long userId;
        private String name;
    }
}
