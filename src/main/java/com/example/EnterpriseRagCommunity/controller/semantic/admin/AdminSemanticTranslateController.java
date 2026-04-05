package com.example.EnterpriseRagCommunity.controller.semantic.admin;

import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateHistoryDTO;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.SemanticTranslateConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/semantic/translate")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminSemanticTranslateController {

    private final SemanticTranslateConfigService semanticTranslateConfigService;
    private final AdministratorService administratorService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_translate','access'))")
    public SemanticTranslateConfigDTO getConfig() {
        return semanticTranslateConfigService.getAdminConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_translate','action'))")
    public SemanticTranslateConfigDTO upsertConfig(@RequestBody SemanticTranslateConfigDTO payload, Principal principal) {
        String username = AdminSemanticControllerSupport.resolveUsername(principal);
        Long userId = AdminSemanticControllerSupport.resolveActorUserId(administratorService, principal);
        return semanticTranslateConfigService.upsertAdminConfig(payload, userId, username);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_translate','access'))")
    public ResponseEntity<Page<SemanticTranslateHistoryDTO>> listHistory(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "userId", required = false) Long userId
    ) {
        return ResponseEntity.ok(semanticTranslateConfigService.listHistory(userId, page, size));
    }
}
