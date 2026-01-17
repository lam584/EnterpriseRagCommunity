package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/moderation/llm")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AdminModerationLlmController {

    private final AdminModerationLlmService service;

    public AdminModerationLlmController(AdminModerationLlmService service) {
        this.service = service;
    }

    @GetMapping("/config")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public LlmModerationConfigDTO getConfig() {
        return service.getConfig();
    }

    @PutMapping("/config")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public LlmModerationConfigDTO upsertConfig(@RequestBody LlmModerationConfigDTO payload, Principal principal) {
        // NOTE: current project doesn't expose userId in Principal directly.
        // We'll store updatedBy as null for now, and keep username in response.
        String username = principal == null ? null : principal.getName();
        return service.upsertConfig(payload, null, username);
    }

    @PostMapping("/test")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public LlmModerationTestResponse test(@RequestBody LlmModerationTestRequest req) {
        return service.test(req);
    }
}
