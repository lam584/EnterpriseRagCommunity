package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/moderation/chunk-review/config")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminModerationChunkReviewConfigController {
    private final ModerationChunkReviewConfigService configService;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_chunk_review','access'))")
    public ModerationChunkReviewConfigDTO getConfig() {
        return configService.getConfig();
    }

    @PutMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_chunk_review','write'))")
    public ModerationChunkReviewConfigDTO updateConfig(@RequestBody ModerationChunkReviewConfigDTO payload) {
        ModerationChunkReviewConfigDTO before = configService.getConfig();
        ModerationChunkReviewConfigDTO after = configService.updateConfig(payload);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "CONFIG_CHANGE",
                "MODERATION_CHUNK_REVIEW_CONFIG",
                null,
                AuditResult.SUCCESS,
                "更新分片审核配置",
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
