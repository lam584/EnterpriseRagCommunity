package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationPolicyConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationPolicyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/moderation/policy")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AdminModerationPolicyController {

    private final AdminModerationPolicyService service;

    public AdminModerationPolicyController(AdminModerationPolicyService service) {
        this.service = service;
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_review','access'))")
    public ModerationPolicyConfigDTO getConfig(@RequestParam("contentType") ContentType contentType) {
        return service.getConfig(contentType);
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_review','access'))")
    public ModerationPolicyConfigDTO upsert(@RequestBody ModerationPolicyConfigDTO payload, Principal principal) {
        String username = principal == null ? null : principal.getName();
        return service.upsert(payload, null, username);
    }
}

