package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.CurrentUserIdResolver;
import com.example.EnterpriseRagCommunity.service.ai.AiSemanticTranslateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiContentTranslateController {

    private final AiSemanticTranslateService aiSemanticTranslateService;
    private final AdministratorService administratorService;

    private Long currentUserIdOrThrow() {
        return CurrentUserIdResolver.currentUserIdOrThrow(
                administratorService,
                () -> new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {
                },
                () -> new IllegalArgumentException("当前用户不存在")
        );
    }

    @PostMapping("/posts/{postId}/translate")
    public SseEmitter translatePost(
            @PathVariable Long postId,
            @RequestParam("targetLang") String targetLang
    ) {
        Long me = currentUserIdOrThrow();
        return aiSemanticTranslateService.translatePostStream(postId, targetLang, me);
    }

    @PostMapping("/comments/{commentId}/translate")
    public SseEmitter translateComment(
            @PathVariable Long commentId,
            @RequestParam("targetLang") String targetLang
    ) {
        Long me = currentUserIdOrThrow();
        return aiSemanticTranslateService.translateCommentStream(commentId, targetLang, me);
    }
}
