package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationSamplesAutoSyncConfigServiceTest {

    private AppSettingsService appSettingsService;
    private ModerationSamplesAutoSyncConfigService service;

    @BeforeEach
    void setUp() {
        appSettingsService = mock(AppSettingsService.class);
        service = new ModerationSamplesAutoSyncConfigService(appSettingsService);
    }

    @Test
    void getConfig_shouldUseDefaultEnabledWhenSettingMissing() {
        when(appSettingsService.getString(ModerationSamplesAutoSyncConfigService.KEY_AUTO_SYNC_ENABLED))
                .thenReturn(Optional.empty());
        when(appSettingsService.getLongOrDefault(ModerationSamplesAutoSyncConfigService.KEY_AUTO_SYNC_INTERVAL_SECONDS, 60))
                .thenReturn(120L);

        ModerationSamplesAutoSyncConfigDTO dto = service.getConfig();

        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getIntervalSeconds()).isEqualTo(120L);
    }

    @Test
    void getConfig_shouldParseEnabledFalse() {
        when(appSettingsService.getString(ModerationSamplesAutoSyncConfigService.KEY_AUTO_SYNC_ENABLED))
                .thenReturn(Optional.of("false"));
        when(appSettingsService.getLongOrDefault(ModerationSamplesAutoSyncConfigService.KEY_AUTO_SYNC_INTERVAL_SECONDS, 60))
                .thenReturn(30L);

        ModerationSamplesAutoSyncConfigDTO dto = service.getConfig();

        assertThat(dto.getEnabled()).isFalse();
        assertThat(dto.getIntervalSeconds()).isEqualTo(30L);
    }

    @Test
    void updateConfig_shouldThrowWhenPayloadNull() {
        assertThatThrownBy(() -> service.updateConfig(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payload is required");
    }

    @Test
    void updateConfig_shouldThrowWhenEnabledNull() {
        ModerationSamplesAutoSyncConfigDTO payload = new ModerationSamplesAutoSyncConfigDTO();
        payload.setEnabled(null);
        payload.setIntervalSeconds(60L);

        assertThatThrownBy(() -> service.updateConfig(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("enabled 不能为空");
    }

    @Test
    void updateConfig_shouldUseDefaultIntervalWhenNull() {
        ModerationSamplesAutoSyncConfigDTO payload = new ModerationSamplesAutoSyncConfigDTO();
        payload.setEnabled(true);
        payload.setIntervalSeconds(null);

        ModerationSamplesAutoSyncConfigDTO out = service.updateConfig(payload);

        assertThat(out.getEnabled()).isTrue();
        assertThat(out.getIntervalSeconds()).isEqualTo(60L);
        verify(appSettingsService).upsertString(ModerationSamplesAutoSyncConfigService.KEY_AUTO_SYNC_ENABLED, "true");
        verify(appSettingsService).upsertString(ModerationSamplesAutoSyncConfigService.KEY_AUTO_SYNC_INTERVAL_SECONDS, "60");
    }

    @Test
    void updateConfig_shouldClampIntervalToMinAndPersistDisabled() {
        ModerationSamplesAutoSyncConfigDTO payload = new ModerationSamplesAutoSyncConfigDTO();
        payload.setEnabled(false);
        payload.setIntervalSeconds(1L);

        ModerationSamplesAutoSyncConfigDTO out = service.updateConfig(payload);

        assertThat(out.getEnabled()).isFalse();
        assertThat(out.getIntervalSeconds()).isEqualTo(5L);
        verify(appSettingsService).upsertString(ModerationSamplesAutoSyncConfigService.KEY_AUTO_SYNC_ENABLED, "false");
        verify(appSettingsService).upsertString(ModerationSamplesAutoSyncConfigService.KEY_AUTO_SYNC_INTERVAL_SECONDS, "5");
    }

    @Test
    void updateConfig_shouldClampIntervalToMax() {
        ModerationSamplesAutoSyncConfigDTO payload = new ModerationSamplesAutoSyncConfigDTO();
        payload.setEnabled(true);
        payload.setIntervalSeconds(9_999L);

        ModerationSamplesAutoSyncConfigDTO out = service.updateConfig(payload);

        assertThat(out.getIntervalSeconds()).isEqualTo(3600L);
        verify(appSettingsService).upsertString(ModerationSamplesAutoSyncConfigService.KEY_AUTO_SYNC_INTERVAL_SECONDS, "3600");
    }

    @Test
    void getIncrementalSyncCursorLastIdOrDefault_shouldDelegateToSettingsService() {
        when(appSettingsService.getLongOrDefault(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_ID, 99L))
                .thenReturn(123L);

        long out = service.getIncrementalSyncCursorLastIdOrDefault(99L);

        assertThat(out).isEqualTo(123L);
    }

    @Test
    void updateIncrementalSyncCursorLastId_shouldSkipWhenLastIdNotPositive() {
        service.updateIncrementalSyncCursorLastId(0);
        service.updateIncrementalSyncCursorLastId(-1);

        verify(appSettingsService, never()).upsertString(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_ID, "0");
        verify(appSettingsService, never()).upsertString(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_ID, "-1");
    }

    @Test
    void updateIncrementalSyncCursorLastId_shouldPersistWhenPositive() {
        service.updateIncrementalSyncCursorLastId(88L);

        verify(appSettingsService).upsertString(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_ID, "88");
    }

    @Test
    void getLastIncrementalSyncAt_shouldReturnStoredValue() {
        when(appSettingsService.getString(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_AT))
                .thenReturn(Optional.of("2026-03-06T00:00:00Z"));

        Optional<String> out = service.getLastIncrementalSyncAt();

        assertThat(out).contains("2026-03-06T00:00:00Z");
    }

    @Test
    void markIncrementalSyncFinished_shouldOnlyPersistLastAtWhenLastIdInvalid() {
        service.markIncrementalSyncFinished(null);

        verify(appSettingsService, never()).upsertString(eq(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_ID), anyString());
        verify(appSettingsService).upsertString(
                eq(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_AT),
                anyString()
        );
    }

    @Test
    void markIncrementalSyncFinished_shouldPersistLastIdAndUtcTimestampWhenValid() {
        service.markIncrementalSyncFinished(66L);

        verify(appSettingsService).upsertString(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_ID, "66");
        ArgumentCaptor<String> atCaptor = ArgumentCaptor.forClass(String.class);
        verify(appSettingsService, times(1)).upsertString(
                org.mockito.ArgumentMatchers.eq(ModerationSamplesAutoSyncConfigService.KEY_INCREMENTAL_SYNC_LAST_AT),
                atCaptor.capture()
        );
        assertThat(OffsetDateTime.parse(atCaptor.getValue()).getOffset().getId()).isEqualTo("Z");
    }
}
