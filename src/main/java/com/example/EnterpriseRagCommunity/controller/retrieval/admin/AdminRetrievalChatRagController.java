package com.example.EnterpriseRagCommunity.controller.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ChatRagAugmentConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/retrieval/chat-rag")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminRetrievalChatRagController {

    private final ChatRagAugmentConfigService chatRagAugmentConfigService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_context','access'))")
    public ChatRagAugmentConfigDTO getConfig() {
        return chatRagAugmentConfigService.getConfigOrDefault();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_retrieval_context','write'))")
    public ChatRagAugmentConfigDTO updateConfig(@RequestBody ChatRagAugmentConfigDTO payload) {
        return chatRagAugmentConfigService.updateConfig(payload);
    }
}
