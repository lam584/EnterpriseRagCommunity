package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.service.ai.PortalChatConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/portal-chat")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminPortalChatConfigController {
    private final PortalChatConfigService portalChatConfigService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','read'))")
    public PortalChatConfigDTO getConfig() {
        return portalChatConfigService.getAdminConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','write'))")
    public PortalChatConfigDTO updateConfig(@RequestBody PortalChatConfigDTO payload) {
        return portalChatConfigService.upsertAdminConfig(payload);
    }
}

