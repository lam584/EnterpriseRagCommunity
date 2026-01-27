package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostRiskTagGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.service.ai.PostRiskTagGenConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/posts")
@RequiredArgsConstructor
public class AiPostRiskTagController {

    private final PostRiskTagGenConfigService postRiskTagGenConfigService;

    @GetMapping("/risk-tag-gen/config")
    public PostRiskTagGenPublicConfigDTO getRiskTagGenConfig() {
        return postRiskTagGenConfigService.getPublicConfig();
    }
}
