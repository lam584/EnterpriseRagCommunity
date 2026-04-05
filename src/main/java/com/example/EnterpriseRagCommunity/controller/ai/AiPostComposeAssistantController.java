package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostComposeStreamRequest;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.CurrentUserIdResolver;
import com.example.EnterpriseRagCommunity.service.ai.AiPostComposeAssistantService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/ai/post-compose")
@RequiredArgsConstructor
public class AiPostComposeAssistantController {

    private final AiPostComposeAssistantService service;
    private final AdministratorService administratorService;

    @PostMapping(value = "/stream", produces = "text/event-stream")
    public void stream(@Valid @RequestBody AiPostComposeStreamRequest req, HttpServletResponse response) throws IOException {
        Long currentUserId = currentUserIdOrThrow();
        service.streamComposeEdit(req, currentUserId, response);
    }

    private Long currentUserIdOrThrow() {
        return CurrentUserIdResolver.currentUserIdOrThrow(
                administratorService,
                () -> new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {},
                () -> new IllegalArgumentException("当前用户不存在")
        );
    }
}
