package com.example.EnterpriseRagCommunity.controller.content.admin;

import com.example.EnterpriseRagCommunity.dto.content.PostComposeConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.content.PostComposeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/content/posts/compose-config")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminPostComposeConfigController {

    private final PostComposeConfigService postComposeConfigService;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_content_posts_compose','access'))")
    public PostComposeConfigDTO getConfig() {
        return postComposeConfigService.getConfig();
    }

    @PutMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_content_posts_compose','write'))")
    public PostComposeConfigDTO updateConfig(@RequestBody PostComposeConfigDTO payload) {
        PostComposeConfigDTO before = postComposeConfigService.getConfig();
        PostComposeConfigDTO after = postComposeConfigService.updateConfig(payload);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "ADMIN_SETTINGS_UPDATE",
                "POST_COMPOSE_CONFIG",
                null,
                AuditResult.SUCCESS,
                "更新发帖表单配置",
                null,
                auditDiffBuilder.build(before, after)
        );
        return after;
    }

    private static String currentUsernameOrNull() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
            String name = auth.getName();
            return name == null || name.isBlank() ? null : name.trim();
        } catch (Exception e) {
            return null;
        }
    }
}
