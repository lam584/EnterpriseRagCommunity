package com.example.EnterpriseRagCommunity.service.content.jobs;

import com.example.EnterpriseRagCommunity.dto.content.HotScoreConfigDTO;
import com.example.EnterpriseRagCommunity.service.content.HotScoreConfigService;
import com.example.EnterpriseRagCommunity.service.content.HotScoresService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;

/**
 * 热榜分数刷新任务：
 * - 每分钟检查一次开关与间隔配置
 * - 满足条件时执行全窗口重算
 */
@Component
public class HotScoresScheduler {
    private final HotScoresService hotScoresService;
    private final HotScoreConfigService hotScoreConfigService;

    public HotScoresScheduler(HotScoresService hotScoresService,
                              ObjectProvider<HotScoreConfigService> hotScoreConfigServiceProvider) {
        Assert.notNull(hotScoresService, "HotScoresService must not be null!");
        Assert.notNull(hotScoreConfigServiceProvider, "HotScoreConfigService provider must not be null!");
        this.hotScoresService = hotScoresService;
        this.hotScoreConfigService = hotScoreConfigServiceProvider.getIfAvailable();
    }

    private volatile Instant lastAutoRefreshAt = Instant.EPOCH;

    @Scheduled(cron = "0 * * * * ?", zone = "Asia/Shanghai")
    public synchronized void configurableRefresh() {
        HotScoreConfigDTO cfg = hotScoreConfigService == null ? null : hotScoreConfigService.getConfigOrDefault();
        boolean enabled = cfg == null || cfg.getAutoRefreshEnabled() == null
                ? HotScoreConfigService.DEFAULT_AUTO_REFRESH_ENABLED
                : cfg.getAutoRefreshEnabled();
        int intervalMinutes = cfg == null || cfg.getAutoRefreshIntervalMinutes() == null
                ? HotScoreConfigService.DEFAULT_AUTO_REFRESH_INTERVAL_MINUTES
                : Math.max(1, cfg.getAutoRefreshIntervalMinutes());

        if (!enabled) {
            return;
        }

        Instant now = Instant.now();
        long elapsedMinutes = Duration.between(lastAutoRefreshAt, now).toMinutes();
        if (elapsedMinutes < intervalMinutes) {
            return;
        }

        hotScoresService.recomputeAllWindowsDaily();
        lastAutoRefreshAt = now;
    }
}

