package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigUpsertRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.LlmPriceConfigAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/ai/prices")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminLlmPriceConfigsController {

    private final LlmPriceConfigAdminService llmPriceConfigAdminService;
    private final AdministratorService administratorService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','read'))")
    public List<AdminLlmPriceConfigDTO> listAll() {
        return llmPriceConfigAdminService.listAll();
    }

    @PutMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','write'))")
    public AdminLlmPriceConfigDTO upsert(@Valid @RequestBody AdminLlmPriceConfigUpsertRequest req, Principal principal) {
        String username = principal == null ? null : principal.getName();
        Long userId = null;
        if (username != null) {
            userId = administratorService.findByUsername(username).map(UsersEntity::getId).orElse(null);
        }
        return llmPriceConfigAdminService.upsert(req, userId);
    }
}

