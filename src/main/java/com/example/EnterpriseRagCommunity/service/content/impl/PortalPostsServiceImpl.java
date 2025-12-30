package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostDetailDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.HotScoresRepository;
import com.example.EnterpriseRagCommunity.service.content.CommentsService;
import com.example.EnterpriseRagCommunity.service.content.PortalPostsService;
import com.example.EnterpriseRagCommunity.service.content.PostInteractionsService;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import com.example.EnterpriseRagCommunity.repository.content.PostViewsDailyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class PortalPostsServiceImpl implements PortalPostsService {

    private static final Logger log = LoggerFactory.getLogger(PortalPostsServiceImpl.class);

    @Autowired
    private PostsService postsService;

    @Autowired
    private PostInteractionsService postInteractionsService;

    @Autowired
    private CommentsService commentsService;

    @Autowired
    private PostViewsDailyRepository postViewsDailyRepository;

    @Autowired(required = false)
    private HotScoresRepository hotScoresRepository;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

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

        // Fill hotScore (if hot scores are enabled).
        // Default to scoreAll so all feeds and detail share one stable "热度分".
        try {
            if (hotScoresRepository != null) {
                hotScoresRepository.findByPostId(postId).ifPresent(h -> dto.setHotScore(h.getScoreAll()));
            }
        } catch (Exception ex) {
            log.debug("Failed to read hotScore for postId={}", postId, ex);
        }

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
    @Transactional
    public PostDetailDTO getById(Long id) {
        PostsEntity e = postsService.getById(id);

        // 浏览量：按自然日聚合 +1（不去重）。
        // 注意：视图计数不应影响主流程，但也不能静默失败；这里记录 warning 便于排查。
        try {
            postViewsDailyRepository.increment(id, LocalDate.now(ZONE));
        } catch (Exception ex) {
            log.warn("Failed to increment post view count. postId={}, day={}", id, LocalDate.now(ZONE), ex);
        }

        return enrichAggregates(toBaseDto(e));
    }
}
