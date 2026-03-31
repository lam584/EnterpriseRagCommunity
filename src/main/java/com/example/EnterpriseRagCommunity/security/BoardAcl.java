package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.repository.content.BoardModeratorsRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("boardAcl")
@RequiredArgsConstructor
public class BoardAcl {

    private final BoardModeratorsRepository boardModeratorsRepository;
    private final PostsRepository postsRepository;
    private final CommentsRepository commentsRepository;
    private final ModerationQueueRepository moderationQueueRepository;
    private final AdministratorService administratorService;

    public boolean canModerateBoard(Long boardId) {
        if (boardId == null) return false;
        Long me = currentUserIdOrNull();
        if (me == null) return false;
        return boardModeratorsRepository.existsByBoardIdAndUserId(boardId, me);
    }

    public boolean canModeratePost(Long postId) {
        if (postId == null) return false;
        var post = postsRepository.findByIdAndIsDeletedFalse(postId).orElse(null);
        if (post == null || post.getBoardId() == null) return false;
        return canModerateBoard(post.getBoardId());
    }

    public boolean canModerateQueueItem(Long queueId) {
        if (queueId == null) return false;
        var q = moderationQueueRepository.findById(queueId).orElse(null);
        if (q == null) return false;
        if (q.getContentType() == ContentType.POST) {
            return canModeratePost(q.getContentId());
        }
        if (q.getContentType() == ContentType.COMMENT) {
            var c = commentsRepository.findById(q.getContentId()).orElse(null);
            if (c == null || Boolean.TRUE.equals(c.getIsDeleted()) || c.getPostId() == null) return false;
            return canModeratePost(c.getPostId());
        }
        return false;
    }

    public Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        String email = auth.getName();
        return administratorService.findByUsername(email).map(u -> u.getId()).orElse(null);
    }
}
