package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateResultDTO;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiSemanticTranslateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiContentTranslateController {

    private final AiSemanticTranslateService aiSemanticTranslateService;
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

    @PostMapping("/posts/{postId}/translate")
    public SemanticTranslateResultDTO translatePost(
            @PathVariable("postId") Long postId,
            @RequestParam("targetLang") String targetLang
    ) {
        Long me = currentUserIdOrThrow();
        return aiSemanticTranslateService.translatePost(postId, targetLang, me);
    }

    @PostMapping("/comments/{commentId}/translate")
    public SemanticTranslateResultDTO translateComment(
            @PathVariable("commentId") Long commentId,
            @RequestParam("targetLang") String targetLang
    ) {
        Long me = currentUserIdOrThrow();
        return aiSemanticTranslateService.translateComment(commentId, targetLang, me);
    }
}

