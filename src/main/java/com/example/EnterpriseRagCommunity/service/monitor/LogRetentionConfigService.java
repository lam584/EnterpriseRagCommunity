package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.LogRetentionConfigDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LogRetentionConfigService {

    public static final String KEY_ENABLED = "monitor.logs.retention.enabled";
    public static final String KEY_KEEP_DAYS = "monitor.logs.retention.keepDays";
    public static final String KEY_MODE = "monitor.logs.retention.mode";

    private final AppSettingsService appSettingsService;

    private static long normalizeKeepDays(long keepDays) {
        long v = keepDays <= 0 ? 90 : keepDays;
        return Math.clamp(v, 1, 3650);
    }

    private static LogRetentionMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) return LogRetentionMode.ARCHIVE_TABLE;
        try {
            return LogRetentionMode.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return LogRetentionMode.ARCHIVE_TABLE;
        }
    }

    @Transactional(readOnly = true)
    public LogRetentionConfigDTO getConfig() {
        boolean enabled = Boolean.parseBoolean(appSettingsService.getString(KEY_ENABLED).orElse("false"));
        long keepDays = appSettingsService.getLongOrDefault(KEY_KEEP_DAYS, 90);
        String modeRaw = appSettingsService.getString(KEY_MODE).orElse(LogRetentionMode.ARCHIVE_TABLE.name());
        LogRetentionMode mode = parseMode(modeRaw);
        keepDays = normalizeKeepDays(keepDays);
        return new LogRetentionConfigDTO(enabled, keepDays, mode);
    }

    @Transactional
    public LogRetentionConfigDTO updateConfig(LogRetentionConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        long keepDays = normalizeKeepDays(payload.keepDays());
        LogRetentionMode mode = payload.mode() == null ? LogRetentionMode.ARCHIVE_TABLE : payload.mode();

        appSettingsService.upsertString(KEY_ENABLED, Boolean.toString(payload.enabled()));
        appSettingsService.upsertString(KEY_KEEP_DAYS, Long.toString(keepDays));
        appSettingsService.upsertString(KEY_MODE, mode.name());
        return new LogRetentionConfigDTO(payload.enabled(), keepDays, mode);
    }
}

