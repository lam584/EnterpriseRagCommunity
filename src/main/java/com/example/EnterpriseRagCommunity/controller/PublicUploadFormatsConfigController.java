package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadFormatsConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.UploadFormatsConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/uploads")
@RequiredArgsConstructor
public class PublicUploadFormatsConfigController {

    private final UploadFormatsConfigService uploadFormatsConfigService;

    @GetMapping("/formats-config")
    public UploadFormatsConfigDTO getFormatsConfig() {
        return uploadFormatsConfigService.getConfig();
    }
}

