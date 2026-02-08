package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostDetailDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.example.EnterpriseRagCommunity.repository.content.HotScoresRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.service.content.CommentsService;
import com.example.EnterpriseRagCommunity.service.content.PortalPostsService;
import com.example.EnterpriseRagCommunity.service.content.PostInteractionsService;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import com.example.EnterpriseRagCommunity.repository.content.PostViewsDailyRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.content.BoardAccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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

    @Autowired
    private ReactionsRepository reactionsRepository;

    @Autowired
    private AdministratorService administratorService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private BoardsRepository boardsRepository;

    @Autowired
    private BoardAccessControlService boardAccessControlService;

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

        dto.setAuthorName(null);
        dto.setAuthorAvatarUrl(null);
        dto.setBoardName(null);

        dto.setHotScore(null);
        dto.setMetadata(e.getMetadata());

        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setPublishedAt(e.getPublishedAt());
        return dto;
    }

    private static String readProfileString(UsersEntity user, String key) {
        if (user == null) return null;
        Map<String, Object> metadata = user.getMetadata();
        if (metadata == null) return null;
        Object profileObj = metadata.get("profile");
        if (!(profileObj instanceof Map)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) profileObj;
        Object v = profile.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private PostDetailDTO enrichDisplay(PostDetailDTO dto, UsersEntity author, BoardsEntity board) {
        if (author != null) {
            String name = author.getUsername();
            dto.setAuthorName(name == null || name.trim().isEmpty() ? null : name.trim());
            dto.setAuthorAvatarUrl(readProfileString(author, "avatarUrl"));
        }
        if (board != null) {
            String name = board.getName();
            dto.setBoardName(name == null || name.trim().isEmpty() ? null : name.trim());
        }
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

    private static <T> Map<Long, T> indexById(Collection<T> rows, java.util.function.Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>();
        if (rows == null) return m;
        for (T r : rows) {
            if (r == null) continue;
            Long id = idFn.apply(r);
            if (id == null) continue;
            m.put(id, r);
        }
        return m;
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
        Page<PostsEntity> rs = postsService
                .query(keyword, postId, searchMode, boardId, status, authorId, createdFrom, createdTo, page, pageSize, sortBy, sortOrderDirection);

        Set<Long> roleIds = boardAccessControlService.currentUserRoleIds();
        var visiblePosts = rs.getContent().stream()
                .filter(e -> e == null || e.getBoardId() == null || boardAccessControlService.canViewBoard(e.getBoardId(), roleIds))
                .toList();

        LinkedHashSet<Long> authorIds = new LinkedHashSet<>();
        LinkedHashSet<Long> boardIds = new LinkedHashSet<>();
        for (PostsEntity e : visiblePosts) {
            if (e == null) continue;
            if (e.getAuthorId() != null) authorIds.add(e.getAuthorId());
            if (e.getBoardId() != null) boardIds.add(e.getBoardId());
        }

        Map<Long, UsersEntity> authorMap = indexById(usersRepository.findByIdInAndIsDeletedFalse(authorIds), UsersEntity::getId);
        Map<Long, BoardsEntity> boardMap = indexById(boardsRepository.findAllById(boardIds), BoardsEntity::getId);

        var content = visiblePosts.stream()
                .map(PortalPostsServiceImpl::toBaseDto)
                .map(dto -> enrichDisplay(dto, authorMap.get(dto.getAuthorId()), boardMap.get(dto.getBoardId())))
                .map(this::enrichAggregates)
                .toList();

        return new PageImpl<>(content, rs.getPageable(), rs.getTotalElements());
    }

    @Override
    @Transactional
    public PostDetailDTO getById(Long id) {
        PostsEntity e = postsService.getById(id);

        // 访问控制：待审核/草稿/驳回/归档 等非已发布内容，仅作者本人可查看
        if (e.getStatus() != PostStatus.PUBLISHED) {
            Long me = null;
            try {
                var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                    String email = auth.getName();
                    me = administratorService.findByUsername(email).map(x -> x.getId()).orElse(null);
                }
            } catch (Exception ignored) {
                me = null;
            }

            boolean isAuthor = me != null && e.getAuthorId() != null && me.equals(e.getAuthorId());
            if (!isAuthor) {
                // 用 404 语义更贴近“不可见”，避免泄露存在性
                throw new IllegalArgumentException("帖子不存在: " + id);
            }
        }

        // 浏览量：仅对可公开访问的帖子累计
        if (e.getStatus() == PostStatus.PUBLISHED) {
            try {
                postViewsDailyRepository.increment(id, LocalDate.now(ZONE));
            } catch (Exception ex) {
                log.warn("Failed to increment post view count. postId={}, day={}", id, LocalDate.now(ZONE), ex);
            }
        }

        if (e.getBoardId() != null) {
            Set<Long> roleIds = boardAccessControlService.currentUserRoleIds();
            if (!boardAccessControlService.canViewBoard(e.getBoardId(), roleIds)) {
                throw new org.springframework.security.access.AccessDeniedException("无权访问该版块");
            }
        }

        UsersEntity author = null;
        BoardsEntity board = null;
        try {
            if (e.getAuthorId() != null) author = usersRepository.findByIdAndIsDeletedFalse(e.getAuthorId()).orElse(null);
        } catch (Exception ignored) {
            author = null;
        }
        try {
            if (e.getBoardId() != null) board = boardsRepository.findById(e.getBoardId()).orElse(null);
        } catch (Exception ignored) {
            board = null;
        }

        return enrichAggregates(enrichDisplay(toBaseDto(e), author, board));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDetailDTO> queryMyBookmarkedPosts(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);

        // 获取 userId：沿用 PostInteractionsServiceImpl 的做法：从 SecurityContext 取 email 再查 users。
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {};
        }
        String email = auth.getName();
        Long userId = administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"))
                .getId();

        Pageable pageable = PageRequest.of(safePage - 1, safePageSize);
        var rs = reactionsRepository.findBookmarkedPostsByUserId(userId, ReactionTargetType.POST, ReactionType.FAVORITE, pageable);

        var content = rs.getContent().stream()
                .map(PortalPostsServiceImpl::toBaseDto)
                .map(dto -> {
                    UsersEntity author = null;
                    BoardsEntity board = null;
                    try {
                        if (dto.getAuthorId() != null) author = usersRepository.findByIdAndIsDeletedFalse(dto.getAuthorId()).orElse(null);
                    } catch (Exception ignored) {
                        author = null;
                    }
                    try {
                        if (dto.getBoardId() != null) board = boardsRepository.findById(dto.getBoardId()).orElse(null);
                    } catch (Exception ignored) {
                        board = null;
                    }
                    return enrichDisplay(dto, author, board);
                })
                .map(this::enrichAggregates)
                .toList();

        return new PageImpl<>(content, rs.getPageable(), rs.getTotalElements());
    }
}
