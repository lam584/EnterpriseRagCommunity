package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.CommentCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.CommentDTO;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.CommentsService;
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
        e.setStatus(CommentStatus.VISIBLE);
        e.setIsDeleted(false);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());

        return toDTO(commentsRepository.save(e));
    }

    @Override
    public long countByPostId(Long postId) {
        if (postId == null) return 0;
        return commentsRepository.countByPostIdAndStatusAndIsDeletedFalse(postId, CommentStatus.VISIBLE);
    }
}
