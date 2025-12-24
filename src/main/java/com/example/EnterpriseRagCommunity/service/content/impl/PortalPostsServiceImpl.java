package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostDetailDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.service.content.CommentsService;
import com.example.EnterpriseRagCommunity.service.content.PortalPostsService;
import com.example.EnterpriseRagCommunity.service.content.PostInteractionsService;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PortalPostsServiceImpl implements PortalPostsService {

    @Autowired
    private PostsService postsService;

    @Autowired
    private PostInteractionsService postInteractionsService;

    @Autowired
    private CommentsService commentsService;

    private static PostDetailDTO toBaseDto(PostsEntity e) {
        PostDetailDTO dto = new PostDetailDTO();
        dto.setId(e.getId());
        dto.setTenantId(e.getTenantId());
        dto.setBoardId(e.getBoardId());
        dto.setAuthorId(e.getAuthorId());

        dto.setTitle(e.getTitle());
        dto.setContent(e.getContent());
        dto.setContentFormat(e.getContentFormat());
        dto.setStatus(e.getStatus());

        // TODO: authorName/boardName can be filled if related tables are available.
        dto.setAuthorName(null);
        dto.setBoardName(null);

        dto.setHotScore(null);
        dto.setMetadata(e.getMetadata());

        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setPublishedAt(e.getPublishedAt());
        return dto;
    }

    private PostDetailDTO enrichAggregates(PostDetailDTO dto) {
        Long postId = dto.getId();
        dto.setCommentCount(commentsService.countByPostId(postId));
        dto.setReactionCount(postInteractionsService.countLikes(postId));
        dto.setFavoriteCount(postInteractionsService.countFavorites(postId));

        // likedByMe/favoritedByMe may throw when anonymous; treat as false for browse.
        try {
            dto.setLikedByMe(postInteractionsService.likedByMe(postId));
        } catch (Exception ignored) {
            dto.setLikedByMe(false);
        }
        try {
            dto.setFavoritedByMe(postInteractionsService.favoritedByMe(postId));
        } catch (Exception ignored) {
            dto.setFavoritedByMe(false);
        }

        return dto;
    }

    @Override
    public Page<PostDetailDTO> query(String keyword,
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
                                    String sortOrderDirection) {
        return postsService
                .query(keyword, postId, searchMode, boardId, status, authorId, createdFrom, createdTo, page, pageSize, sortBy, sortOrderDirection)
                .map(PortalPostsServiceImpl::toBaseDto)
                .map(this::enrichAggregates);
    }

    @Override
    public PostDetailDTO getById(Long id) {
        PostsEntity e = postsService.getById(id);
        return enrichAggregates(toBaseDto(e));
    }
}

