package com.example.EnterpriseRagCommunity.controller.ai.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.EnterpriseRagCommunity.dto.ai.AdminAiModelProbeResultDTO;
import com.example.EnterpriseRagCommunity.service.ai.AdminAiModelProbeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/ai/models")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminAiModelProbeController {

    private final AdminAiModelProbeService service;

    @GetMapping("/probe")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','read'))")
    public AdminAiModelProbeResultDTO probe(
            @RequestParam("kind") String kind,
            @RequestParam("providerId") String providerId,
            @RequestParam("modelName") String modelName,
            @RequestParam(value = "timeoutMs", required = false) Long timeoutMs
    ) {
        return service.probe(kind, providerId, modelName, timeoutMs);
    }
}

