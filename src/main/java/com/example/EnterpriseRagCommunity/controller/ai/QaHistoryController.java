package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.QaMessageDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSearchHitDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSessionDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSessionUpdateRequest;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.QaHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/qa")
@RequiredArgsConstructor
public class QaHistoryController {

    private final QaHistoryService qaHistoryService;
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

    @GetMapping("/sessions")
    public Page<QaSessionDTO> listSessions(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Long me = currentUserIdOrThrow();
        return qaHistoryService.listMySessions(me, page, size);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<QaMessageDTO> listSessionMessages(@PathVariable("sessionId") Long sessionId) {
        Long me = currentUserIdOrThrow();
        return qaHistoryService.getMySessionMessages(me, sessionId);
    }

    @PatchMapping("/sessions/{sessionId}")
    public QaSessionDTO updateSession(@PathVariable("sessionId") Long sessionId, @Valid @RequestBody QaSessionUpdateRequest req) {
        Long me = currentUserIdOrThrow();
        return qaHistoryService.updateMySession(me, sessionId, req);
    }

    @GetMapping("/search")
    public Page<QaSearchHitDTO> search(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Long me = currentUserIdOrThrow();
        return qaHistoryService.searchMyHistory(me, q, page, size);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable("sessionId") Long sessionId) {
        Long me = currentUserIdOrThrow();
        qaHistoryService.deleteMySession(me, sessionId);
        return ResponseEntity.noContent().build();
    }
}
