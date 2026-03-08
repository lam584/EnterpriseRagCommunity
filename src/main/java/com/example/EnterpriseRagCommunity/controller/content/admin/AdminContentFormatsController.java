package com.example.EnterpriseRagCommunity.controller.content.admin;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadFormatsConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.UploadFormatsConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/content/formats")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminContentFormatsController {

    private final UploadFormatsConfigService uploadFormatsConfigService;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_content_formats','access'))")
    public UploadFormatsConfigDTO getConfig() {
        return uploadFormatsConfigService.getConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_content_formats','write'))")
    public UploadFormatsConfigDTO updateConfig(@RequestBody UploadFormatsConfigDTO payload) {
        return uploadFormatsConfigService.updateConfig(payload);
    }
}
