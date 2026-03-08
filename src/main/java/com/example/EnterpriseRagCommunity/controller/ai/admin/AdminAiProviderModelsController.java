package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.AiProviderAddModelRequestDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiProviderModelsDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiUpstreamModelsPreviewRequestDTO;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiProviderModelsAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai/providers")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminAiProviderModelsController {
    private final AiProviderModelsAdminService aiProviderModelsAdminService;
    private final AdministratorService administratorService;

    @GetMapping("/{providerId}/models")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','read'))")
    public AiProviderModelsDTO listModels(@PathVariable("providerId") String providerId) {
        return aiProviderModelsAdminService.listProviderModels(providerId);
    }

    @PostMapping("/{providerId}/models")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','write'))")
    public AiProviderModelsDTO addModel(
            @PathVariable("providerId") String providerId,
            @RequestBody(required = false) AiProviderAddModelRequestDTO payload,
            Principal principal
    ) {
        String username = principal == null ? null : principal.getName();
        Long userId = null;
        if (username != null) {
            userId = administratorService.findByUsername(username).map(u -> u.getId()).orElse(null);
        }
        String purpose = payload == null ? null : payload.getPurpose();
        String modelName = payload == null ? null : payload.getModelName();
        return aiProviderModelsAdminService.addProviderModel(providerId, purpose, modelName, userId);
    }

    @DeleteMapping("/{providerId}/models")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','write'))")
    public AiProviderModelsDTO deleteModel(
            @PathVariable("providerId") String providerId,
            @RequestParam("purpose") String purpose,
            @RequestParam("modelName") String modelName
    ) {
        return aiProviderModelsAdminService.deleteProviderModel(providerId, purpose, modelName);
    }

    @GetMapping("/{providerId}/upstream-models")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','read'))")
    public Map<String, Object> upstreamModels(@PathVariable("providerId") String providerId) {
        return aiProviderModelsAdminService.fetchUpstreamModels(providerId);
    }

    @PostMapping("/upstream-models/preview")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','read'))")
    public Map<String, Object> previewUpstreamModels(@RequestBody AiUpstreamModelsPreviewRequestDTO payload) {
        return aiProviderModelsAdminService.previewUpstreamModels(payload);
    }
}
