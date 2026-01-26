package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostLangLabelSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostLangLabelSuggestResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PostLangLabelGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiPostLangLabelService;
import com.example.EnterpriseRagCommunity.service.ai.PostLangLabelGenConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/posts")
@RequiredArgsConstructor
public class AiPostLangLabelController {

    private final AiPostLangLabelService aiPostLangLabelService;
    private final AdministratorService administratorService;
    private final PostLangLabelGenConfigService postLangLabelGenConfigService;

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

    @PostMapping("/lang-label-suggestions")
    public AiPostLangLabelSuggestResponse langLabelSuggestions(@Valid @RequestBody AiPostLangLabelSuggestRequest req) {
        currentUserIdOrThrow();
        return aiPostLangLabelService.suggestLanguages(req);
    }

    @GetMapping("/lang-label-gen/config")
    public PostLangLabelGenPublicConfigDTO getLangLabelGenConfig() {
        return postLangLabelGenConfigService.getPublicConfig();
    }
}

