package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.HotPostDTO;
import org.springframework.data.domain.Page;

/**
 * 热榜服务：
 * - 计算并写回 hot_scores(score_24h/score_7d/score_all)
 * - 提供前台查询接口
 */
public interface HotScoresService {

    enum Window {
        H24, D7, ALL
    }

    Page<HotPostDTO> listHot(Window window, int page, int pageSize);

    /** 每天全量重算：24h + 7d + all */
    void recomputeAllWindowsDaily();

    /** 每小时更新 24h（自然日口径下等价“刷新今日分数”） */
    void recompute24hHourly();
}

