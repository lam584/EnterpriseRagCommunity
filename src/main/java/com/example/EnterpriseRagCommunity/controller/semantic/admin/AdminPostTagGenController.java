package com.example.EnterpriseRagCommunity.controller.semantic.admin;

import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenHistoryDTO;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.PostTagGenConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/semantic/multi-label")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminPostTagGenController {

    private final PostTagGenConfigService postTagGenConfigService;
    private final AdministratorService administratorService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_multi_label','access'))")
    public PostTagGenConfigDTO getConfig() {
        return postTagGenConfigService.getAdminConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_multi_label','action'))")
    public PostTagGenConfigDTO upsertConfig(@RequestBody PostTagGenConfigDTO payload, Principal principal) {
        String username = principal == null ? null : principal.getName();
        Long userId = null;
        if (username != null) {
            userId = administratorService.findByUsername(username).map(u -> u.getId()).orElse(null);
        }
        return postTagGenConfigService.upsertAdminConfig(payload, userId, username);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_multi_label','access'))")
    public ResponseEntity<Page<PostTagGenHistoryDTO>> listHistory(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "userId", required = false) Long userId
    ) {
        return ResponseEntity.ok(postTagGenConfigService.listHistory(userId, page, size));
    }
}

