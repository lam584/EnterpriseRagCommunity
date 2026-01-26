package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslatePublicConfigDTO;
import com.example.EnterpriseRagCommunity.service.ai.SemanticTranslateConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/translate")
@RequiredArgsConstructor
public class AiTranslateConfigController {

    private final SemanticTranslateConfigService semanticTranslateConfigService;

    @GetMapping("/config")
    public SemanticTranslatePublicConfigDTO getConfig() {
        return semanticTranslateConfigService.getPublicConfig();
    }
}

