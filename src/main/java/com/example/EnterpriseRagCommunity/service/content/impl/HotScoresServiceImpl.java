package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.HotPostDTO;
import com.example.EnterpriseRagCommunity.dto.content.HotScoreConfigDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostDetailDTO;
import com.example.EnterpriseRagCommunity.entity.content.HotScoreRecomputeLogEntity;
import com.example.EnterpriseRagCommunity.entity.content.HotScoresEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.HotScoreRecomputeLogRepository;
import com.example.EnterpriseRagCommunity.repository.content.HotScoresRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostViewsDailyRepository;
import com.example.EnterpriseRagCommunity.service.content.HotScoreConfigService;
import com.example.EnterpriseRagCommunity.service.content.HotScoresService;
import com.example.EnterpriseRagCommunity.service.content.PortalPostsService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HotScoresServiceImpl implements HotScoresService {
    private final HotScoresRepository hotScoresRepository;
    private final HotScoreRecomputeLogRepository hotScoreRecomputeLogRepository;
    private final PostsRepository postsRepository;
    private final PortalPostsService portalPostsService;
    private final PostViewsDailyRepository postViewsDailyRepository;
    private final HotScoreConfigService hotScoreConfigService;

    public HotScoresServiceImpl(HotScoresRepository hotScoresRepository,
                                HotScoreRecomputeLogRepository hotScoreRecomputeLogRepository,
                                PostsRepository postsRepository,
                                PortalPostsService portalPostsService,
                                PostViewsDailyRepository postViewsDailyRepository,
                                ObjectProvider<HotScoreConfigService> hotScoreConfigServiceProvider) {
        Assert.notNull(hotScoresRepository, "HotScoresRepository must not be null!");
        Assert.notNull(hotScoreRecomputeLogRepository, "HotScoreRecomputeLogRepository must not be null!");
        Assert.notNull(postsRepository, "PostsRepository must not be null!");
        Assert.notNull(portalPostsService, "PortalPostsService must not be null!");
        Assert.notNull(postViewsDailyRepository, "PostViewsDailyRepository must not be null!");
        Assert.notNull(hotScoreConfigServiceProvider, "HotScoreConfigService provider must not be null!");
        this.hotScoresRepository = hotScoresRepository;
        this.hotScoreRecomputeLogRepository = hotScoreRecomputeLogRepository;
        this.postsRepository = postsRepository;
        this.portalPostsService = portalPostsService;
        this.postViewsDailyRepository = postViewsDailyRepository;
        this.hotScoreConfigService = hotScoreConfigServiceProvider.getIfAvailable();
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 默认权重兜底值。 */
    private static final double W_LIKE = HotScoreConfigService.DEFAULT_LIKE_WEIGHT;
    private static final double W_FAVORITE = HotScoreConfigService.DEFAULT_FAVORITE_WEIGHT;
    private static final double W_COMMENT = HotScoreConfigService.DEFAULT_COMMENT_WEIGHT;
    private static final double W_VIEW = HotScoreConfigService.DEFAULT_VIEW_WEIGHT;
    private static final double ALL_DECAY_DAYS = HotScoreConfigService.DEFAULT_ALL_DECAY_DAYS;

    private static double log1p(long x) {
        return x <= 0 ? 0.0 : Math.log1p(x);
    }

    private record Weights(double like, double favorite, double comment, double view, double allDecayDays) {}

    private Weights resolveWeights() {
        if (hotScoreConfigService == null) {
            return new Weights(W_LIKE, W_FAVORITE, W_COMMENT, W_VIEW, ALL_DECAY_DAYS);
        }
        HotScoreConfigDTO cfg = hotScoreConfigService.getConfigOrDefault();
        if (cfg == null) {
            return new Weights(W_LIKE, W_FAVORITE, W_COMMENT, W_VIEW, ALL_DECAY_DAYS);
        }
        return new Weights(
                cfg.getLikeWeight() == null ? W_LIKE : cfg.getLikeWeight(),
                cfg.getFavoriteWeight() == null ? W_FAVORITE : cfg.getFavoriteWeight(),
                cfg.getCommentWeight() == null ? W_COMMENT : cfg.getCommentWeight(),
                cfg.getViewWeight() == null ? W_VIEW : cfg.getViewWeight(),
                cfg.getAllDecayDays() == null ? ALL_DECAY_DAYS : cfg.getAllDecayDays()
        );
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
            case D30 -> {
                LocalDateTime now = LocalDateTime.now(ZONE);
                yield new Range(now.minusDays(30), now);
            }
            case M3 -> {
                LocalDateTime now = LocalDateTime.now(ZONE);
                yield new Range(now.minusDays(90), now);
            }
            case M6 -> {
                LocalDateTime now = LocalDateTime.now(ZONE);
                yield new Range(now.minusDays(180), now);
            }
            case Y1 -> {
                LocalDateTime now = LocalDateTime.now(ZONE);
                yield new Range(now.minusDays(365), now);
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
            case D30 -> {
                pr = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "score30d"));
                hsPage = hotScoresRepository.findAllByScore30dGreaterThanOrderByScore30dDesc(0.0, pr);
            }
            case M3 -> {
                pr = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "score90d"));
                hsPage = hotScoresRepository.findAllByScore90dGreaterThanOrderByScore90dDesc(0.0, pr);
            }
            case M6 -> {
                pr = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "score180d"));
                hsPage = hotScoresRepository.findAllByScore180dGreaterThanOrderByScore180dDesc(0.0, pr);
            }
            case Y1 -> {
                pr = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "score365d"));
                hsPage = hotScoresRepository.findAllByScore365dGreaterThanOrderByScore365dDesc(0.0, pr);
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
                        case D30 -> h.getScore30d();
                        case M3 -> h.getScore90d();
                        case M6 -> h.getScore180d();
                        case Y1 -> h.getScore365d();
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
        recomputeAllWindowsDailyWithResult();
    }

    @Override
    @Transactional
    public RecomputeResult recomputeAllWindowsDailyWithResult() {
        LocalDateTime startedAt = LocalDateTime.now(ZONE);
        LocalDate today = LocalDate.now(ZONE);

        List<Window> windows = List.of(Window.H24, Window.D7, Window.D30, Window.M3, Window.M6, Window.Y1, Window.ALL);
        int changedCount = 0;
        int increasedCount = 0;
        int decreasedCount = 0;
        int unchangedCount = 0;
        double increasedScoreDelta = 0.0;
        double decreasedScoreDelta = 0.0;

        for (Window w : windows) {
            RecomputeResult part = recomputeWindowInternal(w, today, false);
            changedCount += part.changedCount();
            increasedCount += part.increasedCount();
            decreasedCount += part.decreasedCount();
            unchangedCount += part.unchangedCount();
            increasedScoreDelta += part.increasedScoreDelta();
            decreasedScoreDelta += part.decreasedScoreDelta();
        }

        LocalDateTime finishedAt = LocalDateTime.now(ZONE);
        long durationMs = Math.max(0, Duration.between(startedAt, finishedAt).toMillis());
        RecomputeResult out = new RecomputeResult(
                "ALL_WINDOWS",
                startedAt,
                finishedAt,
                durationMs,
                changedCount,
                increasedCount,
                decreasedCount,
                unchangedCount,
                increasedScoreDelta,
                decreasedScoreDelta
        );
        saveRecomputeLog(out);
        return out;
    }

    @Override
    @Transactional
    public void recompute24hHourly() {
        recompute24hHourlyWithResult();
    }

    @Override
    @Transactional
    public RecomputeResult recompute24hHourlyWithResult() {
        LocalDate today = LocalDate.now(ZONE);
        return recomputeWindowInternal(Window.H24, today, true);
    }

    @Override
    @Transactional
    public void recomputeWindow(HotScoresService.Window window) {
        recomputeWindowWithResult(window);
    }

    @Override
    @Transactional
    public RecomputeResult recomputeWindowWithResult(HotScoresService.Window window) {
        LocalDate today = LocalDate.now(ZONE);
        return recomputeWindowInternal(window, today, true);
    }

    @Override
    @Transactional
    public Page<RecomputeLogItem> listRecomputeLogs(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        Page<HotScoreRecomputeLogEntity> logs = hotScoreRecomputeLogRepository.findAllByOrderByIdDesc(PageRequest.of(safePage, safeSize));
        return logs.map(this::toRecomputeLogItem);
    }

    private RecomputeResult recomputeWindowInternal(Window window, LocalDate today, boolean persistLog) {
        LocalDateTime startedAt = LocalDateTime.now(ZONE);
        Range r = rangeFor(window, today);
        Weights weights = resolveWeights();

        // 仅对已发布且未删除的帖子计算
        List<Long> postIds = postsRepository.findIdsByStatusAndIsDeletedFalse(PostStatus.PUBLISHED);
        if (postIds.isEmpty()) {
            LocalDateTime finishedAt = LocalDateTime.now(ZONE);
            RecomputeResult out = new RecomputeResult(
                    window.name(),
                    startedAt,
                    finishedAt,
                    Math.max(0, Duration.between(startedAt, finishedAt).toMillis()),
                    0,
                    0,
                    0,
                    0,
                    0.0,
                    0.0
            );
            if (persistLog) {
                saveRecomputeLog(out);
            }
            return out;
        }

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
        int changedCount = 0;
        int increasedCount = 0;
        int decreasedCount = 0;
        int unchangedCount = 0;
        double increasedScoreDelta = 0.0;
        double decreasedScoreDelta = 0.0;

        for (Long postId : postIds) {
            long likes = likeMap.getOrDefault(postId, 0L);
            long favs = favMap.getOrDefault(postId, 0L);
            long cmts = cmtMap.getOrDefault(postId, 0L);
            long views = viewMap.getOrDefault(postId, 0L);

            double raw = weights.like() * log1p(likes)
                    + weights.favorite() * log1p(favs)
                    + weights.comment() * log1p(cmts)
                    + weights.view() * log1p(views);

            HotScoresEntity e = existing.getOrDefault(postId, new HotScoresEntity());
            e.setPostId(postId);
            if (e.getDecayBase() == null) e.setDecayBase(0.85);
            if (e.getScore24h() == null) e.setScore24h(0.0);
            if (e.getScore7d() == null) e.setScore7d(0.0);
            if (e.getScore30d() == null) e.setScore30d(0.0);
            if (e.getScore90d() == null) e.setScore90d(0.0);
            if (e.getScore180d() == null) e.setScore180d(0.0);
            if (e.getScore365d() == null) e.setScore365d(0.0);
            if (e.getScoreAll() == null) e.setScoreAll(0.0);

            double oldScore = scoreOf(e, window);
            if (!Double.isFinite(oldScore)) {
                oldScore = 0.0;
            }

            double newScore;

            switch (window) {
                case H24 -> {
                    e.setScore24h(raw);
                    newScore = raw;
                }
                case D7 -> {
                    e.setScore7d(raw);
                    newScore = raw;
                }
                case D30 -> {
                    e.setScore30d(raw);
                    newScore = raw;
                }
                case M3 -> {
                    e.setScore90d(raw);
                    newScore = raw;
                }
                case M6 -> {
                    e.setScore180d(raw);
                    newScore = raw;
                }
                case Y1 -> {
                    e.setScore365d(raw);
                    newScore = raw;
                }
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
                    double decay = 1.0 + (days / weights.allDecayDays());
                    newScore = raw / decay;
                    e.setScoreAll(newScore);
                }
                default -> newScore = oldScore;
            }

            double delta = newScore - oldScore;
            if (Math.abs(delta) > 1e-9) {
                changedCount++;
                if (delta > 0) {
                    increasedCount++;
                    increasedScoreDelta += delta;
                } else {
                    decreasedCount++;
                    decreasedScoreDelta -= delta;
                }
            } else {
                unchangedCount++;
            }

            e.setLastRecalculatedAt(now);
            existing.put(postId, e);
        }

        hotScoresRepository.saveAll(existing.values());

        LocalDateTime finishedAt = LocalDateTime.now(ZONE);
        RecomputeResult out = new RecomputeResult(
                window.name(),
                startedAt,
                finishedAt,
                Math.max(0, Duration.between(startedAt, finishedAt).toMillis()),
                changedCount,
                increasedCount,
                decreasedCount,
                unchangedCount,
                increasedScoreDelta,
                decreasedScoreDelta
        );
        if (persistLog) {
            saveRecomputeLog(out);
        }
        return out;
    }

    private double scoreOf(HotScoresEntity e, Window window) {
        if (e == null || window == null) {
            return 0.0;
        }
        return switch (window) {
            case H24 -> e.getScore24h() == null ? 0.0 : e.getScore24h();
            case D7 -> e.getScore7d() == null ? 0.0 : e.getScore7d();
            case D30 -> e.getScore30d() == null ? 0.0 : e.getScore30d();
            case M3 -> e.getScore90d() == null ? 0.0 : e.getScore90d();
            case M6 -> e.getScore180d() == null ? 0.0 : e.getScore180d();
            case Y1 -> e.getScore365d() == null ? 0.0 : e.getScore365d();
            case ALL -> e.getScoreAll() == null ? 0.0 : e.getScoreAll();
        };
    }

    private void saveRecomputeLog(RecomputeResult result) {
        HotScoreRecomputeLogEntity entity = new HotScoreRecomputeLogEntity();
        entity.setWindowType(result.window());
        entity.setStartedAt(result.startedAt());
        entity.setFinishedAt(result.finishedAt());
        entity.setDurationMs(result.durationMs());
        entity.setChangedCount(result.changedCount());
        entity.setIncreasedCount(result.increasedCount());
        entity.setDecreasedCount(result.decreasedCount());
        entity.setUnchangedCount(result.unchangedCount());
        entity.setIncreasedScoreDelta(result.increasedScoreDelta());
        entity.setDecreasedScoreDelta(result.decreasedScoreDelta());
        entity.setCreatedAt(LocalDateTime.now(ZONE));
        hotScoreRecomputeLogRepository.save(entity);
    }

    private RecomputeLogItem toRecomputeLogItem(HotScoreRecomputeLogEntity e) {
        return new RecomputeLogItem(
                e.getId(),
                e.getWindowType(),
                e.getStartedAt(),
                e.getFinishedAt(),
                e.getDurationMs() == null ? 0L : Math.max(0L, e.getDurationMs()),
                e.getChangedCount() == null ? 0 : Math.max(0, e.getChangedCount()),
                e.getIncreasedCount() == null ? 0 : Math.max(0, e.getIncreasedCount()),
                e.getDecreasedCount() == null ? 0 : Math.max(0, e.getDecreasedCount()),
                e.getUnchangedCount() == null ? 0 : Math.max(0, e.getUnchangedCount()),
                e.getIncreasedScoreDelta() == null ? 0.0 : Math.max(0.0, e.getIncreasedScoreDelta()),
                e.getDecreasedScoreDelta() == null ? 0.0 : Math.max(0.0, e.getDecreasedScoreDelta()),
                e.getCreatedAt()
        );
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        return ReactionCountSupport.toCountMap(rows);
    }
}
