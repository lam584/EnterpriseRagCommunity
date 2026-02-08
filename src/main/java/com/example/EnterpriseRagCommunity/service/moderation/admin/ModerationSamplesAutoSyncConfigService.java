package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ModerationSamplesAutoSyncConfigService {

    public static final String KEY_AUTO_SYNC_ENABLED = "moderation.samples.autoSync.enabled";
    public static final String KEY_AUTO_SYNC_INTERVAL_SECONDS = "moderation.samples.autoSync.intervalSeconds";

    public static final String KEY_INCREMENTAL_SYNC_LAST_ID = "moderation.samples.incrementalSync.lastId";
    public static final String KEY_INCREMENTAL_SYNC_LAST_AT = "moderation.samples.incrementalSync.lastAt";

    private final AppSettingsService appSettingsService;

    @Transactional(readOnly = true)
    public ModerationSamplesAutoSyncConfigDTO getConfig() {
        ModerationSamplesAutoSyncConfigDTO dto = new ModerationSamplesAutoSyncConfigDTO();
        String enabled = appSettingsService.getString(KEY_AUTO_SYNC_ENABLED).orElse("true");
        dto.setEnabled(Boolean.parseBoolean(enabled));
        dto.setIntervalSeconds(appSettingsService.getLongOrDefault(KEY_AUTO_SYNC_INTERVAL_SECONDS, 60));
        return dto;
    }

    @Transactional
    public ModerationSamplesAutoSyncConfigDTO updateConfig(ModerationSamplesAutoSyncConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        if (payload.getEnabled() == null) throw new IllegalArgumentException("enabled 不能为空");
        long interval = payload.getIntervalSeconds() == null ? 60 : payload.getIntervalSeconds();
        interval = Math.max(5, Math.min(3600, interval));

        appSettingsService.upsertString(KEY_AUTO_SYNC_ENABLED, payload.getEnabled() ? "true" : "false");
        appSettingsService.upsertString(KEY_AUTO_SYNC_INTERVAL_SECONDS, Long.toString(interval));

        ModerationSamplesAutoSyncConfigDTO out = new ModerationSamplesAutoSyncConfigDTO();
        out.setEnabled(payload.getEnabled());
        out.setIntervalSeconds(interval);
        return out;
    }

    @Transactional(readOnly = true)
    public long getIncrementalSyncCursorLastIdOrDefault(long defaultValue) {
        return appSettingsService.getLongOrDefault(KEY_INCREMENTAL_SYNC_LAST_ID, defaultValue);
    }

    @Transactional
    public void updateIncrementalSyncCursorLastId(long lastId) {
        if (lastId <= 0) return;
        appSettingsService.upsertString(KEY_INCREMENTAL_SYNC_LAST_ID, Long.toString(lastId));
    }

    @Transactional(readOnly = true)
    public Optional<String> getLastIncrementalSyncAt() {
        return appSettingsService.getString(KEY_INCREMENTAL_SYNC_LAST_AT);
    }

    @Transactional
    public void markIncrementalSyncFinished(Long lastId) {
        if (lastId != null && lastId > 0) {
            appSettingsService.upsertString(KEY_INCREMENTAL_SYNC_LAST_ID, Long.toString(lastId));
        }
        String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
        appSettingsService.upsertString(KEY_INCREMENTAL_SYNC_LAST_AT, now);
    }
}
