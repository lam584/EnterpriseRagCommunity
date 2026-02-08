package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.service.moderation.admin.ModerationSamplesAutoSyncConfigService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class ModerationSamplesAutoSyncJob {

    private final ModerationSamplesAutoSyncConfigService configService;
    private final ModerationSamplesSyncService syncService;

    private final AtomicLong lastRunAtMs = new AtomicLong(0);

    @Scheduled(fixedDelayString = "${app.moderation.samples.autoSync.poll-ms:5000}")
    public void tick() {
        ModerationSamplesAutoSyncConfigDTO cfg = configService.getConfig();
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return;

        long intervalMs = (cfg.getIntervalSeconds() == null ? 60 : cfg.getIntervalSeconds()) * 1000L;
        intervalMs = Math.max(5000L, Math.min(3_600_000L, intervalMs));

        long now = System.currentTimeMillis();
        long prev = lastRunAtMs.get();
        if (now - prev < intervalMs) return;
        if (!lastRunAtMs.compareAndSet(prev, now)) return;

        syncService.syncIncremental(true, 200, null);
    }
}
