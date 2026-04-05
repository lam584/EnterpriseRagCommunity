package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.CurrentUserIdResolver;
import com.example.EnterpriseRagCommunity.service.ai.AiPostTitleService;
import com.example.EnterpriseRagCommunity.service.ai.PostTitleGenConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/posts")
@RequiredArgsConstructor
public class AiPostTitleController {

    private final AiPostTitleService aiPostTitleService;
    private final AdministratorService administratorService;
    private final PostTitleGenConfigService postTitleGenConfigService;

    private Long currentUserIdOrThrow() {
        return CurrentUserIdResolver.currentUserIdOrThrow(
                administratorService,
                () -> new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {},
                () -> new IllegalArgumentException("当前用户不存在")
        );
    }

    @PostMapping("/title-suggestions")
    public AiPostTitleSuggestResponse titleSuggestions(@Valid @RequestBody AiPostTitleSuggestRequest req) {
        Long me = currentUserIdOrThrow();
        return aiPostTitleService.suggestTitles(req, me);
    }

    @GetMapping("/title-gen/config")
    public PostTitleGenPublicConfigDTO getTitleGenConfig() {
        return postTitleGenConfigService.getPublicConfig();
    }
}

