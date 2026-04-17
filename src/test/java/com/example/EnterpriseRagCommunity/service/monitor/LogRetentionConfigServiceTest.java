package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.LogRetentionConfigDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogRetentionConfigServiceTest {

    @Mock
    private AppSettingsService appSettingsService;

    @InjectMocks
    private LogRetentionConfigService service;

    @Test
    void getConfig_shouldReturnDefaultsWhenSettingsMissing() {
        when(appSettingsService.getString(LogRetentionConfigService.KEY_ENABLED)).thenReturn(Optional.empty());
        when(appSettingsService.getLongOrDefault(LogRetentionConfigService.KEY_KEEP_DAYS, 90)).thenReturn(90L);
        when(appSettingsService.getString(LogRetentionConfigService.KEY_MODE)).thenReturn(Optional.empty());
        when(appSettingsService.getLongOrDefault(LogRetentionConfigService.KEY_MAX_PER_RUN, 5000)).thenReturn(5000L);
        when(appSettingsService.getString(LogRetentionConfigService.KEY_AUDIT_LOGS_ENABLED)).thenReturn(Optional.empty());
        when(appSettingsService.getString(LogRetentionConfigService.KEY_ACCESS_LOGS_ENABLED)).thenReturn(Optional.empty());
        when(appSettingsService.getString(LogRetentionConfigService.KEY_PURGE_ARCHIVED_ENABLED)).thenReturn(Optional.empty());
        when(appSettingsService.getLongOrDefault(LogRetentionConfigService.KEY_PURGE_ARCHIVED_KEEP_DAYS, 365)).thenReturn(365L);

        LogRetentionConfigDTO cfg = service.getConfig();

        assertThat(cfg.enabled()).isFalse();
        assertThat(cfg.keepDays()).isEqualTo(90);
        assertThat(cfg.mode()).isEqualTo(LogRetentionMode.ARCHIVE_TABLE);
        assertThat(cfg.maxPerRun()).isEqualTo(5000);
        assertThat(cfg.auditLogsEnabled()).isTrue();
        assertThat(cfg.accessLogsEnabled()).isTrue();
        assertThat(cfg.purgeArchivedEnabled()).isFalse();
        assertThat(cfg.purgeArchivedKeepDays()).isEqualTo(365);
    }

    @Test
    void updateConfig_shouldNormalizeAndPersistAllFields() {
        LogRetentionConfigDTO payload = new LogRetentionConfigDTO(
                true,
                -1,
                null,
                999_999,
                true,
                false,
                true,
                -9
        );

        LogRetentionConfigDTO saved = service.updateConfig(payload);

        assertThat(saved.enabled()).isTrue();
        assertThat(saved.keepDays()).isEqualTo(90);
        assertThat(saved.mode()).isEqualTo(LogRetentionMode.ARCHIVE_TABLE);
        assertThat(saved.maxPerRun()).isEqualTo(100_000);
        assertThat(saved.auditLogsEnabled()).isTrue();
        assertThat(saved.accessLogsEnabled()).isFalse();
        assertThat(saved.purgeArchivedEnabled()).isTrue();
        assertThat(saved.purgeArchivedKeepDays()).isEqualTo(365);

        verify(appSettingsService).upsertString(eq(LogRetentionConfigService.KEY_ENABLED), eq("true"));
        verify(appSettingsService).upsertString(eq(LogRetentionConfigService.KEY_KEEP_DAYS), eq("90"));
        verify(appSettingsService).upsertString(eq(LogRetentionConfigService.KEY_MODE), eq("ARCHIVE_TABLE"));
        verify(appSettingsService).upsertString(eq(LogRetentionConfigService.KEY_MAX_PER_RUN), eq("100000"));
        verify(appSettingsService).upsertString(eq(LogRetentionConfigService.KEY_AUDIT_LOGS_ENABLED), eq("true"));
        verify(appSettingsService).upsertString(eq(LogRetentionConfigService.KEY_ACCESS_LOGS_ENABLED), eq("false"));
        verify(appSettingsService).upsertString(eq(LogRetentionConfigService.KEY_PURGE_ARCHIVED_ENABLED), eq("true"));
        verify(appSettingsService).upsertString(eq(LogRetentionConfigService.KEY_PURGE_ARCHIVED_KEEP_DAYS), eq("365"));
    }
}
