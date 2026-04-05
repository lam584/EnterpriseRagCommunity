package com.example.EnterpriseRagCommunity.controller.semantic.admin;

import com.example.EnterpriseRagCommunity.dto.ai.PostLangLabelGenConfigDTO;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.PostLangLabelGenConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/semantic/lang-label")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminPostLangLabelGenController {

    private final PostLangLabelGenConfigService postLangLabelGenConfigService;
    private final AdministratorService administratorService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_multi_label','access'))")
    public PostLangLabelGenConfigDTO getConfig() {
        return postLangLabelGenConfigService.getAdminConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_multi_label','action'))")
    public PostLangLabelGenConfigDTO upsertConfig(@RequestBody PostLangLabelGenConfigDTO payload, Principal principal) {
        String username = AdminSemanticControllerSupport.resolveUsername(principal);
        Long userId = AdminSemanticControllerSupport.resolveActorUserId(administratorService, principal);
        return postLangLabelGenConfigService.upsertAdminConfig(payload, userId, username);
    }
}
