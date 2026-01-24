package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatRegenerateStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.QaMessageUpdateRequest;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiChatService;
import com.example.EnterpriseRagCommunity.service.ai.QaMessageService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/ai/qa/messages")
@RequiredArgsConstructor
public class QaMessageController {

    private final QaMessageService qaMessageService;
    private final AiChatService aiChatService;
    private final AdministratorService administratorService;

    private Long currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {};
        }
        String email = auth.getName();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"))
                .getId();
    }

    @PatchMapping("/{messageId}")
    public ResponseEntity<Void> updateMessage(@PathVariable("messageId") Long messageId, @Valid @RequestBody QaMessageUpdateRequest req) {
        Long me = currentUserIdOrThrow();
        qaMessageService.updateMyMessage(me, messageId, req.getContent());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable("messageId") Long messageId) {
        Long me = currentUserIdOrThrow();
        qaMessageService.deleteMyMessage(me, messageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{questionMessageId}/regenerate/stream", produces = "text/event-stream")
    public void regenerate(
            @PathVariable("questionMessageId") Long questionMessageId,
            @Valid @RequestBody AiChatRegenerateStreamRequest req,
            HttpServletResponse response
    ) throws IOException {
        Long me = currentUserIdOrThrow();
        aiChatService.streamRegenerate(questionMessageId, req, me, response);
    }
}

