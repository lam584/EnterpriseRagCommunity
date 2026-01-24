package com.example.EnterpriseRagCommunity.controller.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.RagAutoSyncConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/retrieval/rag-sync")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminRetrievalRagSyncController {

    private final RagAutoSyncConfigService configService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','access'))")
    public ResponseEntity<RagAutoSyncConfigDTO> getConfig() {
        return ResponseEntity.ok(configService.getConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_index','action'))")
    public ResponseEntity<RagAutoSyncConfigDTO> updateConfig(@RequestBody(required = false) RagAutoSyncConfigDTO payload) {
        return ResponseEntity.ok(configService.updateConfig(payload));
    }
}

