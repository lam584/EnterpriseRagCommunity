package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.CommentCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.CommentDTO;
import org.springframework.data.domain.Page;

public interface CommentsService {
    Page<CommentDTO> listByPostId(Long postId, int page, int pageSize);

    CommentDTO createForPost(Long postId, CommentCreateRequest req);

    long countByPostId(Long postId);
}

