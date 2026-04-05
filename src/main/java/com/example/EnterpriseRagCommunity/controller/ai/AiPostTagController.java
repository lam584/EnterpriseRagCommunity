package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostTagSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTagSuggestResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.CurrentUserIdResolver;
import com.example.EnterpriseRagCommunity.service.ai.AiPostTagService;
import com.example.EnterpriseRagCommunity.service.ai.PostTagGenConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/posts")
@RequiredArgsConstructor
public class AiPostTagController {

    private final AiPostTagService aiPostTagService;
    private final AdministratorService administratorService;
    private final PostTagGenConfigService postTagGenConfigService;

    private Long currentUserIdOrThrow() {
        return CurrentUserIdResolver.currentUserIdOrThrow(
                administratorService,
                () -> new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {
                },
                () -> new IllegalArgumentException("当前用户不存在")
        );
    }

    @PostMapping("/tag-suggestions")
    public AiPostTagSuggestResponse tagSuggestions(@Valid @RequestBody AiPostTagSuggestRequest req) {
        Long me = currentUserIdOrThrow();
        return aiPostTagService.suggestTags(req, me);
    }

    @GetMapping("/tag-gen/config")
    public PostTagGenPublicConfigDTO getTagGenConfig() {
        return postTagGenConfigService.getPublicConfig();
    }
}
