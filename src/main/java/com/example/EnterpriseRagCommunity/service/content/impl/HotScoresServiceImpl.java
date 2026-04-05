package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.HotPostDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostDetailDTO;
import com.example.EnterpriseRagCommunity.entity.content.HotScoresEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.HotScoresRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostViewsDailyRepository;
import com.example.EnterpriseRagCommunity.service.content.HotScoresService;
import com.example.EnterpriseRagCommunity.service.content.PortalPostsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HotScoresServiceImpl implements HotScoresService {
    private final HotScoresRepository hotScoresRepository;
    private final PostsRepository postsRepository;
    private final PortalPostsService portalPostsService;
    private final PostViewsDailyRepository postViewsDailyRepository;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 权重（Option B：log 缩放）。可后续迁移到配置项。 */
    private static final double W_LIKE = 1.0;
    private static final double W_FAVORITE = 2.0;
    private static final double W_COMMENT = 3.0;

    /** 浏览权重：通常比点赞低，但能反映曝光；可按运营效果调整 */
    private static final double W_VIEW = 0.25;

    /** score_all 的时间衰减：每 30 天衰减到 1/(1+1)=0.5（温和） */
    private static final double ALL_DECAY_DAYS = 30.0;

    private static double log1p(long x) {
        return x <= 0 ? 0.0 : Math.log1p(x);
    }

    private record Range(LocalDateTime fromInclusive, LocalDateTime toExclusive) {}

    private static Range rangeFor(HotScoresService.Window window, LocalDate today) {
        return switch (window) {
            case H24 -> new Range(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            case D7 -> {
                // rolling 7 days：必须包含最近 24 小时（避免“自然日口径”导致 7d 榜比 24h 还少）
                LocalDateTime now = LocalDateTime.now(ZONE);
                yield new Range(now.minusDays(7), now);
            }
            case ALL -> new Range(null, null);
        };
    }

    @Override
    public Page<HotPostDTO> listHot(HotScoresService.Window window, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.clamp(pageSize, 1, 100);

        PageRequest pr;
        Page<HotScoresEntity> hsPage;

        // 规则：24h/7d 如果 score=0 则不进入榜单；ALL 先不过滤（可在需要时改为 >0）
        switch (window) {
            case H24 -> {
                pr = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "score24h"));
                hsPage = hotScoresRepository.findAllByScore24hGreaterThanOrderByScore24hDesc(0.0, pr);
            }
            case D7 -> {
                pr = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "score7d"));
                hsPage = hotScoresRepository.findAllByScore7dGreaterThanOrderByScore7dDesc(0.0, pr);
            }
            case ALL -> {
                pr = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "scoreAll"));
                hsPage = hotScoresRepository.findAll(pr);
            }
            default -> {
                pr = PageRequest.of(safePage - 1, safeSize);
                hsPage = hotScoresRepository.findAll(pr);
            }
        }

        List<Long> postIds = hsPage.getContent().stream().map(HotScoresEntity::getPostId).toList();

        Map<Long, PostDetailDTO> postMap = new HashMap<>();
        for (Long postId : postIds) {
            try {
                PostDetailDTO dto = portalPostsService.getById(postId);
                if (dto.getStatus() == PostStatus.PUBLISHED) {
                    postMap.put(postId, dto);
                }
            } catch (Exception ignored) {
            }
        }

        List<HotPostDTO> content = hsPage.getContent().stream()
                .filter(h -> postMap.containsKey(h.getPostId()))
                .map(h -> {
                    HotPostDTO dto = new HotPostDTO();
                    dto.setPost(postMap.get(h.getPostId()));
                    dto.setScore(switch (window) {
                        case H24 -> h.getScore24h();
                        case D7 -> h.getScore7d();
                        case ALL -> h.getScoreAll();
                    });
                    // 也把分数塞到 PostDetailDTO.hotScore，前台老逻辑可能用得到
                    dto.getPost().setHotScore(dto.getScore());
                    return dto;
                })
                .toList();

        // 注意：当前页内容做了过滤（未发布/异常帖子会被剔除）。
        // 如果仍然返回 hsPage.getTotalElements()，前端会看到不可靠的 totalPages，从而误以为条数更少/无法翻页。
        // 这里做一个“安全 total”：不夸大并尽量保持分页体验。
        long safeTotal = hsPage.getTotalElements();
        if (safeTotal < content.size()) safeTotal = content.size();
        if (safeTotal > 0 && content.isEmpty() && safePage == 1) safeTotal = 0;

        return new PageImpl<>(content, hsPage.getPageable(), safeTotal);
    }

    @Override
    @Transactional
    public void recomputeAllWindowsDaily() {
        LocalDate today = LocalDate.now(ZONE);
        recomputeWindow(HotScoresService.Window.H24, today);
        recomputeWindow(HotScoresService.Window.D7, today);
        recomputeWindow(HotScoresService.Window.ALL, today);
    }

    @Override
    @Transactional
    public void recompute24hHourly() {
        // 自然日口径：每小时更新“今天(00:00~24:00)”的分数
        LocalDate today = LocalDate.now(ZONE);
        recomputeWindow(HotScoresService.Window.H24, today);
    }

    private void recomputeWindow(HotScoresService.Window window, LocalDate today) {
        Range r = rangeFor(window, today);

        // 仅对已发布且未删除的帖子计算
        List<Long> postIds = postsRepository.findIdsByStatusAndIsDeletedFalse(PostStatus.PUBLISHED);
        if (postIds.isEmpty()) return;

        // 1) 聚合统计（DB 端 group by）
        Map<Long, Long> likeMap;
        Map<Long, Long> favMap;
        Map<Long, Long> cmtMap;
        Map<Long, Long> viewMap;

        if (window == HotScoresService.Window.ALL) {
            likeMap = toCountMap(hotScoresRepository.aggregateLikesAll());
            favMap = toCountMap(hotScoresRepository.aggregateFavoritesAll());
            cmtMap = toCountMap(hotScoresRepository.aggregateCommentsAll());
            viewMap = toCountMap(postViewsDailyRepository.aggregateViewsAll());
        } else {
            likeMap = toCountMap(hotScoresRepository.aggregateLikesBetween(r.fromInclusive, r.toExclusive));
            favMap = toCountMap(hotScoresRepository.aggregateFavoritesBetween(r.fromInclusive, r.toExclusive));
            cmtMap = toCountMap(hotScoresRepository.aggregateCommentsBetween(r.fromInclusive, r.toExclusive));

            // post_views_daily 是按 day 聚合的（没有小时/分钟粒度）。
            // 为了避免 rolling 窗口在同一天内 toDay==today 导致“今天整天被排除”（从而出现 7d 榜比 24h 还少），
            // 这里把上界扩成“包含 r.toExclusive 所在自然日”的 [fromDay, toDayExclusive)。
            LocalDate fromDay = r.fromInclusive.toLocalDate();
            LocalDate toDayExclusive = r.toExclusive.toLocalDate().plusDays(1);
            viewMap = toCountMap(postViewsDailyRepository.aggregateViewsBetweenDays(fromDay, toDayExclusive));
        }

        // 2) 读取已有 hot_scores，做 upsert
        Map<Long, HotScoresEntity> existing = hotScoresRepository.findByPostIdIn(postIds)
                .stream()
                .collect(Collectors.toMap(HotScoresEntity::getPostId, Function.identity()));

        LocalDateTime now = LocalDateTime.now(ZONE);

        for (Long postId : postIds) {
            long likes = likeMap.getOrDefault(postId, 0L);
            long favs = favMap.getOrDefault(postId, 0L);
            long cmts = cmtMap.getOrDefault(postId, 0L);
            long views = viewMap.getOrDefault(postId, 0L);

            double raw = W_LIKE * log1p(likes)
                    + W_FAVORITE * log1p(favs)
                    + W_COMMENT * log1p(cmts)
                    + W_VIEW * log1p(views);

            HotScoresEntity e = existing.getOrDefault(postId, new HotScoresEntity());
            e.setPostId(postId);
            if (e.getDecayBase() == null) e.setDecayBase(0.85);
            if (e.getScore24h() == null) e.setScore24h(0.0);
            if (e.getScore7d() == null) e.setScore7d(0.0);
            if (e.getScoreAll() == null) e.setScoreAll(0.0);

            switch (window) {
                case H24 -> e.setScore24h(raw);
                case D7 -> e.setScore7d(raw);
                case ALL -> {
                    // 温和时间衰减：按发布天数衰减
                    double days = 0.0;
                    try {
                        var post = postsRepository.findById(postId).orElse(null);
                        if (post != null && post.getPublishedAt() != null) {
                            days = Duration.between(post.getPublishedAt(), now).toDays();
                        }
                    } catch (Exception ignored) {
                    }
                    double decay = 1.0 + (days / ALL_DECAY_DAYS);
                    e.setScoreAll(raw / decay);
                }
            }

            e.setLastRecalculatedAt(now);
            existing.put(postId, e);
        }

        hotScoresRepository.saveAll(existing.values());
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        return ReactionCountSupport.toCountMap(rows);
    }
}
