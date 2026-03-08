package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.PromptBatchRequest;
import com.example.EnterpriseRagCommunity.dto.ai.PromptBatchResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PromptContentDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PromptContentUpdateRequest;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.PromptsAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/prompts")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminPromptsController {
    private final PromptsAdminService promptsAdminService;
    private final AdministratorService administratorService;

    @PostMapping("/batch")
    @PreAuthorize(
            "hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_title_gen','access'))" +
                    " or hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_multi_label','access'))" +
                    " or hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_translate','access'))" +
                    " or hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))" +
                    " or hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','read'))"
    )
    public PromptBatchResponse batchGet(@RequestBody(required = false) PromptBatchRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");
        return promptsAdminService.batchGetByCodes(req.getCodes());
    }

    @PutMapping("/{promptCode}/content")
    @PreAuthorize(
            "hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_title_gen','action'))" +
                    " or hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_multi_label','action'))" +
                    " or hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_translate','action'))" +
                    " or hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))" +
                    " or hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','write'))"
    )
    public PromptContentDTO updateContent(
            @PathVariable("promptCode") String promptCode,
            @RequestBody PromptContentUpdateRequest req,
            Principal principal
    ) {
        String username = principal == null ? null : principal.getName();
        Long userId = null;
        if (username != null) {
            userId = administratorService.findByUsername(username).map(u -> u.getId()).orElse(null);
        }
        try {
            return promptsAdminService.updateContent(promptCode, req, userId);
        } catch (java.util.NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
