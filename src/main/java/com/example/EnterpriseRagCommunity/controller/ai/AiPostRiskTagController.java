package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostRiskTagSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostRiskTagSuggestResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PostRiskTagGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiPostRiskTagService;
import com.example.EnterpriseRagCommunity.service.ai.PostRiskTagGenConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/posts")
@RequiredArgsConstructor
public class AiPostRiskTagController {

    private final PostRiskTagGenConfigService postRiskTagGenConfigService;
    private final AiPostRiskTagService aiPostRiskTagService;
    private final AdministratorService administratorService;

    private Long currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {
            };
        }
        String email = auth.getName();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"))
                .getId();
    }

    @PostMapping("/risk-tag-suggestions")
    public AiPostRiskTagSuggestResponse riskTagSuggestions(@Valid @RequestBody AiPostRiskTagSuggestRequest req) {
        currentUserIdOrThrow();
        return aiPostRiskTagService.suggestRiskTags(req);
    }

    @GetMapping("/risk-tag-gen/config")
    public PostRiskTagGenPublicConfigDTO getRiskTagGenConfig() {
        return postRiskTagGenConfigService.getPublicConfig();
    }
}
