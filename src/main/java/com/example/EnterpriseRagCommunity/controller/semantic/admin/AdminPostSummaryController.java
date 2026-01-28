package com.example.EnterpriseRagCommunity.controller.semantic.admin;

import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenHistoryDTO;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.PostSummaryGenConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/semantic/summary")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminPostSummaryController {

    private final PostSummaryGenConfigService postSummaryGenConfigService;
    private final AdministratorService administratorService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_summary','access'))")
    public PostSummaryGenConfigDTO getConfig() {
        return postSummaryGenConfigService.getAdminConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_summary','action'))")
    public PostSummaryGenConfigDTO upsertConfig(@RequestBody PostSummaryGenConfigDTO payload, Principal principal) {
        String username = principal == null ? null : principal.getName();
        Long userId = null;
        if (username != null) {
            userId = administratorService.findByUsername(username).map(u -> u.getId()).orElse(null);
        }
        return postSummaryGenConfigService.upsertAdminConfig(payload, userId, username);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_summary','access'))")
    public ResponseEntity<Page<PostSummaryGenHistoryDTO>> listHistory(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "postId", required = false) Long postId
    ) {
        return ResponseEntity.ok(postSummaryGenConfigService.listHistory(postId, page, size));
    }
}
