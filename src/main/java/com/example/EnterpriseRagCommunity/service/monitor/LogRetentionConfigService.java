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
    public static final String KEY_MAX_PER_RUN = "monitor.logs.retention.maxPerRun";
    public static final String KEY_AUDIT_LOGS_ENABLED = "monitor.logs.retention.auditLogsEnabled";
    public static final String KEY_ACCESS_LOGS_ENABLED = "monitor.logs.retention.accessLogsEnabled";
    public static final String KEY_PURGE_ARCHIVED_ENABLED = "monitor.logs.retention.purgeArchivedEnabled";
    public static final String KEY_PURGE_ARCHIVED_KEEP_DAYS = "monitor.logs.retention.purgeArchivedKeepDays";

    private static final int DEFAULT_MAX_PER_RUN = 5000;
    private static final long DEFAULT_PURGE_ARCHIVED_KEEP_DAYS = 365;

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

    private static int normalizeMaxPerRun(long raw) {
        long v = raw <= 0 ? DEFAULT_MAX_PER_RUN : raw;
        return Math.clamp(v, 100, 100000);
    }

    private static long normalizePurgeArchivedKeepDays(long keepDays) {
        long v = keepDays <= 0 ? DEFAULT_PURGE_ARCHIVED_KEEP_DAYS : keepDays;
        return Math.clamp(v, 1, 3650);
    }

    @Transactional(readOnly = true)
    public LogRetentionConfigDTO getConfig() {
        boolean enabled = Boolean.parseBoolean(appSettingsService.getString(KEY_ENABLED).orElse("false"));
        long keepDays = appSettingsService.getLongOrDefault(KEY_KEEP_DAYS, 90);
        String modeRaw = appSettingsService.getString(KEY_MODE).orElse(LogRetentionMode.ARCHIVE_TABLE.name());
        LogRetentionMode mode = parseMode(modeRaw);
        int maxPerRun = normalizeMaxPerRun(appSettingsService.getLongOrDefault(KEY_MAX_PER_RUN, DEFAULT_MAX_PER_RUN));
        boolean auditLogsEnabled = Boolean.parseBoolean(appSettingsService.getString(KEY_AUDIT_LOGS_ENABLED).orElse("true"));
        boolean accessLogsEnabled = Boolean.parseBoolean(appSettingsService.getString(KEY_ACCESS_LOGS_ENABLED).orElse("true"));
        boolean purgeArchivedEnabled = Boolean.parseBoolean(appSettingsService.getString(KEY_PURGE_ARCHIVED_ENABLED).orElse("false"));
        long purgeArchivedKeepDays = normalizePurgeArchivedKeepDays(
            appSettingsService.getLongOrDefault(KEY_PURGE_ARCHIVED_KEEP_DAYS, DEFAULT_PURGE_ARCHIVED_KEEP_DAYS)
        );
        keepDays = normalizeKeepDays(keepDays);
        return new LogRetentionConfigDTO(
            enabled,
            keepDays,
            mode,
            maxPerRun,
            auditLogsEnabled,
            accessLogsEnabled,
            purgeArchivedEnabled,
            purgeArchivedKeepDays
        );
    }

    @Transactional
    public LogRetentionConfigDTO updateConfig(LogRetentionConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        long keepDays = normalizeKeepDays(payload.keepDays());
        LogRetentionMode mode = payload.mode() == null ? LogRetentionMode.ARCHIVE_TABLE : payload.mode();
        int maxPerRun = normalizeMaxPerRun(payload.maxPerRun());
        boolean auditLogsEnabled = payload.auditLogsEnabled();
        boolean accessLogsEnabled = payload.accessLogsEnabled();
        boolean purgeArchivedEnabled = payload.purgeArchivedEnabled();
        long purgeArchivedKeepDays = normalizePurgeArchivedKeepDays(payload.purgeArchivedKeepDays());

        appSettingsService.upsertString(KEY_ENABLED, Boolean.toString(payload.enabled()));
        appSettingsService.upsertString(KEY_KEEP_DAYS, Long.toString(keepDays));
        appSettingsService.upsertString(KEY_MODE, mode.name());
        appSettingsService.upsertString(KEY_MAX_PER_RUN, Integer.toString(maxPerRun));
        appSettingsService.upsertString(KEY_AUDIT_LOGS_ENABLED, Boolean.toString(auditLogsEnabled));
        appSettingsService.upsertString(KEY_ACCESS_LOGS_ENABLED, Boolean.toString(accessLogsEnabled));
        appSettingsService.upsertString(KEY_PURGE_ARCHIVED_ENABLED, Boolean.toString(purgeArchivedEnabled));
        appSettingsService.upsertString(KEY_PURGE_ARCHIVED_KEEP_DAYS, Long.toString(purgeArchivedKeepDays));
        return new LogRetentionConfigDTO(
                payload.enabled(),
                keepDays,
                mode,
                maxPerRun,
                auditLogsEnabled,
                accessLogsEnabled,
                purgeArchivedEnabled,
                purgeArchivedKeepDays
        );
    }
}

