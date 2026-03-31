package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationConfidenceFallbackConfigDTO;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationFallbackService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/moderation/fallback")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AdminModerationFallbackController {

    private final AdminModerationFallbackService service;

    public AdminModerationFallbackController(AdminModerationFallbackService service) {
        this.service = service;
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_review','access'))")
    public ModerationConfidenceFallbackConfigDTO getConfig() {
        return service.getConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_review','access'))")
    public ModerationConfidenceFallbackConfigDTO upsert(@RequestBody ModerationConfidenceFallbackConfigDTO payload, Principal principal) {
        String username = principal == null ? null : principal.getName();
        return service.upsert(payload, null, username);
    }
}
