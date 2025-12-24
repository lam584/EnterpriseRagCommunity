package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.PostDetailDTO;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

/**
 * Portal-facing posts service that returns aggregated fields
 * (commentCount/reactionCount/favoriteCount + liked/favorited flags).
 */
public interface PortalPostsService {

    Page<PostDetailDTO> query(String keyword,
                             Long postId,
                             String searchMode,
                             Long boardId,
                             PostStatus status,
                             Long authorId,
                             LocalDate createdFrom,
                             LocalDate createdTo,
                             int page,
                             int pageSize,
                             String sortBy,
                             String sortOrderDirection);

    PostDetailDTO getById(Long id);
}

