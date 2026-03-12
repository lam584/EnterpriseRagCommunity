package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagAutoSyncConfigServiceTest {

    @Test
    void getConfig_shouldReadDefaultsAndStoredValues() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        RagAutoSyncConfigService service = new RagAutoSyncConfigService(appSettingsService);

        when(appSettingsService.getString(RagAutoSyncConfigService.KEY_ENABLED)).thenReturn(Optional.empty(), Optional.of("false"));
        when(appSettingsService.getLongOrDefault(RagAutoSyncConfigService.KEY_INTERVAL_SECONDS, 30)).thenReturn(30L, 99L);

        RagAutoSyncConfigDTO d1 = service.getConfig();
        RagAutoSyncConfigDTO d2 = service.getConfig();
        assertThat(d1.getEnabled()).isTrue();
        assertThat(d1.getIntervalSeconds()).isEqualTo(30);
        assertThat(d2.getEnabled()).isFalse();
        assertThat(d2.getIntervalSeconds()).isEqualTo(99);
    }

    @Test
    void updateConfig_shouldValidateAndClampInterval() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        RagAutoSyncConfigService service = new RagAutoSyncConfigService(appSettingsService);

        assertThatThrownBy(() -> service.updateConfig(null)).isInstanceOf(IllegalArgumentException.class);
        RagAutoSyncConfigDTO missingEnabled = new RagAutoSyncConfigDTO();
        assertThatThrownBy(() -> service.updateConfig(missingEnabled)).isInstanceOf(IllegalArgumentException.class);

        RagAutoSyncConfigDTO low = new RagAutoSyncConfigDTO();
        low.setEnabled(true);
        low.setIntervalSeconds(1L);
        RagAutoSyncConfigDTO lowOut = service.updateConfig(low);
        assertThat(lowOut.getIntervalSeconds()).isEqualTo(5L);
        verify(appSettingsService).upsertString(RagAutoSyncConfigService.KEY_ENABLED, "true");
        verify(appSettingsService).upsertString(RagAutoSyncConfigService.KEY_INTERVAL_SECONDS, "5");

        RagAutoSyncConfigDTO high = new RagAutoSyncConfigDTO();
        high.setEnabled(false);
        high.setIntervalSeconds(99999L);
        RagAutoSyncConfigDTO highOut = service.updateConfig(high);
        assertThat(highOut.getIntervalSeconds()).isEqualTo(3600L);

        RagAutoSyncConfigDTO none = new RagAutoSyncConfigDTO();
        none.setEnabled(true);
        none.setIntervalSeconds(null);
        RagAutoSyncConfigDTO noneOut = service.updateConfig(none);
        assertThat(noneOut.getIntervalSeconds()).isEqualTo(30L);
    }
}
