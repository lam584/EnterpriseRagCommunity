package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.PostComposeConfigDTO;
import com.example.EnterpriseRagCommunity.service.content.PostComposeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/posts")
@RequiredArgsConstructor
public class PublicPostComposeConfigController {

    private final PostComposeConfigService postComposeConfigService;

    @GetMapping("/compose-config")
    public PostComposeConfigDTO getComposeConfig() {
        return postComposeConfigService.getConfig();
    }
}

