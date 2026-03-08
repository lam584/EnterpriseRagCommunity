package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.service.moderation.admin.ModerationSamplesAutoSyncConfigService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesSyncService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationSamplesAutoSyncJobTest {

    @Test
    void tick_shouldReturnWhenConfigNullOrDisabled() {
        ModerationSamplesAutoSyncConfigService configService = mock(ModerationSamplesAutoSyncConfigService.class);
        ModerationSamplesSyncService syncService = mock(ModerationSamplesSyncService.class);
        ModerationSamplesAutoSyncJob job = new ModerationSamplesAutoSyncJob(configService, syncService);

        when(configService.getConfig()).thenReturn(null);
        job.tick();
        verify(syncService, never()).syncIncremental(eq(true), eq(200), eq(null));

        ModerationSamplesAutoSyncConfigDTO disabled = new ModerationSamplesAutoSyncConfigDTO();
        disabled.setEnabled(false);
        when(configService.getConfig()).thenReturn(disabled);
        job.tick();
        verify(syncService, never()).syncIncremental(eq(true), eq(200), eq(null));
    }

    @Test
    void tick_shouldSyncWhenEnabledAndIntervalElapsed() {
        ModerationSamplesAutoSyncConfigService configService = mock(ModerationSamplesAutoSyncConfigService.class);
        ModerationSamplesSyncService syncService = mock(ModerationSamplesSyncService.class);
        ModerationSamplesAutoSyncJob job = new ModerationSamplesAutoSyncJob(configService, syncService);

        ModerationSamplesAutoSyncConfigDTO cfg = new ModerationSamplesAutoSyncConfigDTO();
        cfg.setEnabled(true);
        cfg.setIntervalSeconds(1L);
        when(configService.getConfig()).thenReturn(cfg);

        job.tick();

        verify(syncService).syncIncremental(true, 200, null);
    }

    @Test
    void tick_shouldThrottleWhenIntervalNotElapsed_forDefaultAndMaxClamp() throws Exception {
        ModerationSamplesAutoSyncConfigService configService = mock(ModerationSamplesAutoSyncConfigService.class);
        ModerationSamplesSyncService syncService = mock(ModerationSamplesSyncService.class);
        ModerationSamplesAutoSyncJob job = new ModerationSamplesAutoSyncJob(configService, syncService);

        ModerationSamplesAutoSyncConfigDTO defaultIntervalCfg = new ModerationSamplesAutoSyncConfigDTO();
        defaultIntervalCfg.setEnabled(true);
        defaultIntervalCfg.setIntervalSeconds(null);
        when(configService.getConfig()).thenReturn(defaultIntervalCfg);
        setLastRunTime(job, System.currentTimeMillis());
        job.tick();
        verify(syncService, never()).syncIncremental(eq(true), eq(200), eq(null));

        ModerationSamplesAutoSyncConfigDTO maxClampCfg = new ModerationSamplesAutoSyncConfigDTO();
        maxClampCfg.setEnabled(true);
        maxClampCfg.setIntervalSeconds(7_200L);
        when(configService.getConfig()).thenReturn(maxClampCfg);
        setLastRunTime(job, System.currentTimeMillis() - 3_599_000L);
        job.tick();
        verify(syncService, never()).syncIncremental(eq(true), eq(200), eq(null));
    }

    @Test
    void tick_shouldReturnWhenCompareAndSetFails() throws Exception {
        ModerationSamplesAutoSyncConfigService configService = mock(ModerationSamplesAutoSyncConfigService.class);
        ModerationSamplesSyncService syncService = mock(ModerationSamplesSyncService.class);
        ModerationSamplesAutoSyncJob job = new ModerationSamplesAutoSyncJob(configService, syncService);

        ModerationSamplesAutoSyncConfigDTO cfg = new ModerationSamplesAutoSyncConfigDTO();
        cfg.setEnabled(true);
        cfg.setIntervalSeconds(1L);
        when(configService.getConfig()).thenReturn(cfg);

        AtomicLong lastRunAtMs = mock(AtomicLong.class);
        when(lastRunAtMs.get()).thenReturn(0L);
        when(lastRunAtMs.compareAndSet(eq(0L), anyLong())).thenReturn(false);
        setLastRunRef(job, lastRunAtMs);

        job.tick();

        verify(lastRunAtMs).compareAndSet(eq(0L), anyLong());
        verify(syncService, never()).syncIncremental(eq(true), eq(200), eq(null));
    }

    private static void setLastRunTime(ModerationSamplesAutoSyncJob job, long ms) throws Exception {
        Field f = ModerationSamplesAutoSyncJob.class.getDeclaredField("lastRunAtMs");
        f.setAccessible(true);
        AtomicLong al = (AtomicLong) f.get(job);
        al.set(ms);
    }

    private static void setLastRunRef(ModerationSamplesAutoSyncJob job, AtomicLong value) throws Exception {
        Field f = ModerationSamplesAutoSyncJob.class.getDeclaredField("lastRunAtMs");
        f.setAccessible(true);
        f.set(job, value);
    }
}
