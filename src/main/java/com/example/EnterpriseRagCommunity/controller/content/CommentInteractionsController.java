package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.CommentToggleResponseDTO;
import com.example.EnterpriseRagCommunity.entity.content.ReactionsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/comments")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class CommentInteractionsController {

    @Autowired
    private ReactionsRepository reactionsRepository;

    @Autowired
    private AdministratorService administratorService;

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

    @PostMapping("/{commentId}/like")
    public CommentToggleResponseDTO toggleLike(@PathVariable("commentId") Long commentId) {
        if (commentId == null) throw new IllegalArgumentException("commentId 不能为空");
        Long me = currentUserIdOrThrow();

        boolean existed = reactionsRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(
                me, ReactionTargetType.COMMENT, commentId, ReactionType.LIKE
        );
        if (existed) {
            reactionsRepository.deleteByUserIdAndTargetTypeAndTargetIdAndType(
                    me, ReactionTargetType.COMMENT, commentId, ReactionType.LIKE
            );
        } else {
            ReactionsEntity e = new ReactionsEntity();
            e.setUserId(me);
            e.setTargetType(ReactionTargetType.COMMENT);
            e.setTargetId(commentId);
            e.setType(ReactionType.LIKE);
            e.setCreatedAt(LocalDateTime.now());
            reactionsRepository.save(e);
        }

        long likeCount = reactionsRepository.countByTargetTypeAndTargetIdAndType(
                ReactionTargetType.COMMENT, commentId, ReactionType.LIKE
        );
        return new CommentToggleResponseDTO(!existed, likeCount);
    }
}

