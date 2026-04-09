package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.HotPostDTO;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;

/**
 * 热榜服务：
 * - 计算并写回 hot_scores(score_24h/score_7d/score_all)
 * - 提供前台查询接口
 */
public interface HotScoresService {

    enum Window {
        H24, D7, D30, M3, M6, Y1, ALL
    }

        record RecomputeResult(
            String window,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            long durationMs,
            int changedCount,
            int increasedCount,
            int decreasedCount,
            int unchangedCount,
            double increasedScoreDelta,
            double decreasedScoreDelta
        ) {
        }

        record RecomputeLogItem(
            Long id,
            String window,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            long durationMs,
            int changedCount,
            int increasedCount,
            int decreasedCount,
            int unchangedCount,
            double increasedScoreDelta,
            double decreasedScoreDelta,
            LocalDateTime createdAt
        ) {
        }

    Page<HotPostDTO> listHot(Window window, int page, int pageSize);

    /** 每天全量重算：24h + 7d + all */
    void recomputeAllWindowsDaily();

    RecomputeResult recomputeAllWindowsDailyWithResult();

    /** 每小时更新 24h（自然日口径下等价“刷新今日分数”） */
    void recompute24hHourly();

    RecomputeResult recompute24hHourlyWithResult();

    /** 手动触发某个窗口的重算。 */
    void recomputeWindow(Window window);

    RecomputeResult recomputeWindowWithResult(Window window);

    Page<RecomputeLogItem> listRecomputeLogs(int page, int size);
}

