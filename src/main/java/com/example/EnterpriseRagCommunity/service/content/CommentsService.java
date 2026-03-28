package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.CommentCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.CommentDTO;
import org.springframework.data.domain.Page;

public interface CommentsService {
    Page<CommentDTO> listByPostId(Long postId, int page, int pageSize, boolean includeMinePending);

    CommentDTO createForPost(Long postId, CommentCreateRequest req);

    void deleteOwnComment(Long commentId);

    Page<CommentDTO> listMyComments(int page, int pageSize, String keyword);
    long countByPostId(Long postId);
}

