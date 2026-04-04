package com.example.EnterpriseRagCommunity.controller.safety.admin;

import com.example.EnterpriseRagCommunity.dto.safety.DependencyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.dto.safety.DependencyCircuitBreakerUpdateRequest;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/safety/dependency-circuit-breakers")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminDependencyCircuitBreakerController {

    private final AppSettingsService appSettingsService;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @GetMapping("/{dependency}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_safety_circuit_breaker','read'))")
    public DependencyCircuitBreakerConfigDTO get(@PathVariable String dependency) {
        String dep = normalizeDep(dependency);
        DependencyCircuitBreakerConfigDTO out = new DependencyCircuitBreakerConfigDTO();
        out.setDependency(dep);
        out.setFailureThreshold((int) appSettingsService.getLongOrDefault("deps." + dep + ".failureThreshold", 5));
        out.setCooldownSeconds((int) appSettingsService.getLongOrDefault("deps." + dep + ".cooldownSeconds", 30));
        return out;
    }

    @PutMapping("/{dependency}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_safety_circuit_breaker','write'))")
    public DependencyCircuitBreakerConfigDTO update(@PathVariable String dependency, @Valid @RequestBody DependencyCircuitBreakerUpdateRequest req, HttpServletRequest request) {
        String dep = normalizeDep(dependency);
        DependencyCircuitBreakerConfigDTO cfg = req.getConfig();
        if (cfg == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "config 不能为空");

        Map<String, Object> before = Map.of(
                "dependency", dep,
                "failureThreshold", (int) appSettingsService.getLongOrDefault("deps." + dep + ".failureThreshold", 5),
                "cooldownSeconds", (int) appSettingsService.getLongOrDefault("deps." + dep + ".cooldownSeconds", 30)
        );

        Integer ft0 = cfg.getFailureThreshold();
        Integer cd0 = cfg.getCooldownSeconds();
        int ft = ft0 == null ? 5 : Math.clamp(ft0, 0, 1000);
        int cd = cd0 == null ? 30 : Math.clamp(cd0, 0, 3600);

        appSettingsService.upsertString("deps." + dep + ".failureThreshold", String.valueOf(ft));
        appSettingsService.upsertString("deps." + dep + ".cooldownSeconds", String.valueOf(cd));

        Actor actor = resolveActor(request);
        try {
            Map<String, Object> after = Map.of(
                    "dependency", dep,
                    "failureThreshold", ft,
                    "cooldownSeconds", cd
            );
            Map<String, Object> details = new java.util.LinkedHashMap<>(auditDiffBuilder.build(before, after));
            details.put("reason", req.getReason());
            auditLogWriter.write(
                    actor.userId,
                    actor.name,
                    "DEPENDENCY_CIRCUIT_BREAKER_UPDATE",
                    "APP_SETTINGS",
                    null,
                    AuditResult.SUCCESS,
                    "更新依赖熔断配置",
                    null,
                    details
            );
        } catch (Exception ignored) {
        }

        DependencyCircuitBreakerConfigDTO out = new DependencyCircuitBreakerConfigDTO();
        out.setDependency(dep);
        out.setFailureThreshold(ft);
        out.setCooldownSeconds(cd);
        return out;
    }

    private static String normalizeDep(String dep) {
        if (dep == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dependency 不能为空");
        String t = dep.trim();
        if (t.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dependency 不能为空");
        return t.toUpperCase(Locale.ROOT);
    }

    private static final class Actor {
        private Long userId;
        private String name;
    }

    private static Actor resolveActor(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或会话已过期");
        }
        Actor a = new Actor();
        a.name = auth.getName();
        if (request != null && request.getSession(false) != null) {
            Object v = request.getSession(false).getAttribute("auth.userId");
            if (v instanceof Number n) a.userId = n.longValue();
        }
        return a;
    }
}
