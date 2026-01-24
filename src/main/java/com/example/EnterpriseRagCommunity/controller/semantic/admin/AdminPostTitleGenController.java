package com.example.EnterpriseRagCommunity.controller.semantic.admin;

import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenHistoryDTO;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.PostTitleGenConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/semantic/title-gen")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminPostTitleGenController {

    private final PostTitleGenConfigService postTitleGenConfigService;
    private final AdministratorService administratorService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_title_gen','access'))")
    public PostTitleGenConfigDTO getConfig() {
        return postTitleGenConfigService.getAdminConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_title_gen','action'))")
    public PostTitleGenConfigDTO upsertConfig(@RequestBody PostTitleGenConfigDTO payload, Principal principal) {
        String username = principal == null ? null : principal.getName();
        Long userId = null;
        if (username != null) {
            userId = administratorService.findByUsername(username).map(u -> u.getId()).orElse(null);
        }
        return postTitleGenConfigService.upsertAdminConfig(payload, userId, username);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_title_gen','access'))")
    public ResponseEntity<Page<PostTitleGenHistoryDTO>> listHistory(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "userId", required = false) Long userId
    ) {
        return ResponseEntity.ok(postTitleGenConfigService.listHistory(userId, page, size));
    }
}
