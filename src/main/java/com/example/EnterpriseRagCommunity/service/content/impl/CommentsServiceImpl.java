package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.CommentCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.CommentDTO;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.CommentsService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CommentsServiceImpl implements CommentsService {

    @Autowired
    private CommentsRepository commentsRepository;

    @Autowired
    private AdministratorService administratorService;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private NotificationsService notificationsService;

    @Autowired
    private AdminModerationQueueService adminModerationQueueService;

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

    private static CommentDTO toDTO(CommentsEntity e) {
        CommentDTO dto = new CommentDTO();
        dto.setId(e.getId());
        dto.setPostId(e.getPostId());
        dto.setParentId(e.getParentId());
        dto.setAuthorId(e.getAuthorId());
        dto.setContent(e.getContent());
        dto.setStatus(e.getStatus() == null ? null : e.getStatus().name());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    @Override
    public Page<CommentDTO> listByPostId(Long postId, int page, int pageSize) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");
        int safePage = Math.max(page, 1);
        int safePageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        return commentsRepository
                .findByPostIdAndStatusAndIsDeletedFalse(postId, CommentStatus.VISIBLE, pageable)
                .map(CommentsServiceImpl::toDTO);
    }

    @Override
    @Transactional
    public CommentDTO createForPost(Long postId, CommentCreateRequest req) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");
        if (req == null) throw new IllegalArgumentException("参数不能为空");

        Long me = currentUserIdOrThrow();

        CommentsEntity e = new CommentsEntity();
        e.setPostId(postId);
        e.setParentId(req.getParentId());
        e.setAuthorId(me);
        e.setContent(req.getContent());

        // 业务规则：用户评论默认进入待审核状态；审核通过后再改为 VISIBLE
        e.setStatus(CommentStatus.PENDING);

        e.setIsDeleted(false);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());

        CommentsEntity saved = commentsRepository.save(e);

        // 新增：写入审核队列（防重复）
        adminModerationQueueService.ensureEnqueuedComment(saved.getId());

        // 回复通知：仅“有人评论了我发布的帖子（顶层评论）”才通知；多级回复暂不通知。
        if (req.getParentId() == null) {
            PostsEntity post = postsRepository.findById(postId).orElse(null);
            if (post != null && post.getAuthorId() != null && !me.equals(post.getAuthorId())) {
                String postTitle = post.getTitle() == null ? "" : post.getTitle();
                String title = "有人评论了你的帖子";
                String content = (postTitle.isBlank() ? "" : ("帖子《" + postTitle + "》")) + "收到了新的评论：" + req.getContent();
                // 避免 content 太长（表字段是 TEXT，但前端展示也需要可读性）
                if (content.length() > 500) {
                    content = content.substring(0, 500) + "...";
                }
                notificationsService.createNotification(post.getAuthorId(), "REPLY_POST", title, content);
            }
        }

        return toDTO(saved);
    }

    @Override
    public long countByPostId(Long postId) {
        if (postId == null) return 0;
        return commentsRepository.countByPostIdAndStatusAndIsDeletedFalse(postId, CommentStatus.VISIBLE);
    }
}
