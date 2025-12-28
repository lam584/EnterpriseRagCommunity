package com.example.EnterpriseRagCommunity.service.content.jobs;

import com.example.EnterpriseRagCommunity.service.content.HotScoresService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热榜分数刷新任务：
 * - 每天 00:05 全量刷新 24h/7d/all（避开 00:00 的数据库抖动/备份窗口）
 * - 每小时整点+1分钟 做一次 24h 的增量刷新（自然日口径：更新“今天”）
 */
@Component
public class HotScoresScheduler {

    @Autowired
    private HotScoresService hotScoresService;

    /** 每天 00:05 执行 */
    @Scheduled(cron = "0 5 0 * * ?", zone = "Asia/Shanghai")
    public void dailyRecompute() {
        hotScoresService.recomputeAllWindowsDaily();
    }

    /** 每小时 01 分执行 */
    @Scheduled(cron = "0 1 * * * ?", zone = "Asia/Shanghai")
    public void hourlyRecompute24h() {
        hotScoresService.recompute24hHourly();
    }
}

