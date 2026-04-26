package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.CommentToggleResponseDTO;
import com.example.EnterpriseRagCommunity.entity.content.ReactionsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.CurrentUserIdResolver;
import com.example.EnterpriseRagCommunity.service.access.CurrentUsernameResolver;
import com.example.EnterpriseRagCommunity.service.content.CommentsService;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class CommentInteractionsController {
    private final ReactionsRepository reactionsRepository;
    private final AdministratorService administratorService;
    private final AuditLogWriter auditLogWriter;
    private final CommentsService commentsService;

    private Long currentUserIdOrThrow() {
        return CurrentUserIdResolver.currentUserIdOrThrow(
                administratorService,
                () -> new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {},
                () -> new IllegalArgumentException("当前用户不存在")
        );
    }

    @PostMapping("/{commentId}/like")
    public CommentToggleResponseDTO toggleLike(@PathVariable("commentId") Long commentId) {
        if (commentId == null) throw new IllegalArgumentException("commentId 不能为空");
        Long me = null;
        String actorName = null;
        try {
            actorName = CurrentUsernameResolver.currentUsernameOrNull();
            me = currentUserIdOrThrow();

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
            boolean liked = !existed;

            auditLogWriter.write(
                    me,
                    actorName,
                    "COMMENT_LIKE_TOGGLE",
                    "COMMENT",
                    commentId,
                    AuditResult.SUCCESS,
                    liked ? "点赞评论" : "取消点赞评论",
                    null,
                    Map.of(
                            "liked", liked,
                            "likeCount", likeCount
                    )
            );
            return new CommentToggleResponseDTO(liked, likeCount);
        } catch (RuntimeException e) {
            auditLogWriter.write(
                    me,
                    actorName,
                    "COMMENT_LIKE_TOGGLE",
                    "COMMENT",
                    commentId,
                    AuditResult.FAIL,
                    e.getMessage(),
                    null,
                    Map.of()
            );
            throw e;
        }
    }

    @DeleteMapping("/{commentId}")
    public void deleteMyComment(@PathVariable("commentId") Long commentId) {
        commentsService.deleteOwnComment(commentId);
    }

    @GetMapping("/mine")
    public org.springframework.data.domain.Page<com.example.EnterpriseRagCommunity.dto.content.CommentDTO> listMyComments(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return commentsService.listMyComments(page, pageSize, keyword);
    }
}
