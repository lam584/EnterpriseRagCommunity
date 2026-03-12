package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridRetrievalConfigServiceTest {

    @Test
    void getConfig_shouldReturnDefaultWhenBothKeysMissingOrJsonBroken() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        HybridRetrievalConfigService service = new HybridRetrievalConfigService(appSettingsService, objectMapper);

        when(appSettingsService.getString(HybridRetrievalConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.empty(), Optional.of("{bad"));
        when(appSettingsService.getString(HybridRetrievalConfigService.KEY_CONFIG_JSON_LEGACY)).thenReturn(Optional.empty(), Optional.of("{}"));

        HybridRetrievalConfigDTO cfg1 = service.getConfig();
        HybridRetrievalConfigDTO cfg2 = service.getConfig();

        assertThat(cfg1.getFusionMode()).isEqualTo("RRF");
        assertThat(cfg1.getMaxDocs()).isEqualTo(500);
        assertThat(cfg2.getRrfK()).isEqualTo(60);
        assertThat(service.getConfigOrDefault().getHybridK()).isEqualTo(12);
    }

    @Test
    void normalizeConfig_shouldClampAndFixCoupledFields() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        HybridRetrievalConfigService service = new HybridRetrievalConfigService(appSettingsService, new ObjectMapper());

        HybridRetrievalConfigDTO payload = new HybridRetrievalConfigDTO();
        payload.setEnabled(null);
        payload.setBm25K(-1);
        payload.setVecK(2001);
        payload.setFileVecEnabled(false);
        payload.setFileVecK(99);
        payload.setHybridK(9999);
        payload.setFusionMode("bad");
        payload.setBm25TitleBoost(Double.NaN);
        payload.setBm25ContentBoost(Double.POSITIVE_INFINITY);
        payload.setBm25Weight(-5.0);
        payload.setVecWeight(999.0);
        payload.setFileVecWeight(null);
        payload.setRrfK(-9);
        payload.setRerankEnabled(true);
        payload.setRerankModel("  ");
        payload.setRerankTemperature(3.0);
        payload.setRerankTopP(-2.0);
        payload.setRerankK(600);
        payload.setRerankTimeoutMs(10);
        payload.setRerankSlowThresholdMs(200000);
        payload.setMaxDocs(3);
        payload.setPerDocMaxTokens(2);
        payload.setMaxInputTokens(0);

        HybridRetrievalConfigDTO out = service.normalizeConfig(payload);
        assertThat(out.getEnabled()).isFalse();
        assertThat(out.getBm25K()).isEqualTo(0);
        assertThat(out.getVecK()).isEqualTo(1000);
        assertThat(out.getFileVecEnabled()).isFalse();
        assertThat(out.getFileVecK()).isEqualTo(0);
        assertThat(out.getFusionMode()).isEqualTo("RRF");
        assertThat(out.getBm25TitleBoost()).isEqualTo(2.0);
        assertThat(out.getBm25ContentBoost()).isEqualTo(1.0);
        assertThat(out.getBm25Weight()).isEqualTo(0.0);
        assertThat(out.getVecWeight()).isEqualTo(100.0);
        assertThat(out.getFileVecWeight()).isEqualTo(1.0);
        assertThat(out.getRrfK()).isEqualTo(1);
        assertThat(out.getRerankModel()).isNull();
        assertThat(out.getRerankTemperature()).isEqualTo(2.0);
        assertThat(out.getRerankTopP()).isEqualTo(0.0);
        assertThat(out.getRerankK()).isEqualTo(3);
        assertThat(out.getRerankTimeoutMs()).isEqualTo(1000);
        assertThat(out.getRerankSlowThresholdMs()).isEqualTo(120000);
        assertThat(out.getHybridK()).isEqualTo(3);
        assertThat(out.getPerDocMaxTokens()).isEqualTo(100);
        assertThat(out.getMaxInputTokens()).isEqualTo(1000);
    }

    @Test
    void updateConfig_shouldPersistBothKeysAndThrowOnFailures() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        HybridRetrievalConfigService service = new HybridRetrievalConfigService(appSettingsService, objectMapper);

        assertThatThrownBy(() -> service.updateConfig(null)).isInstanceOf(IllegalArgumentException.class);

        HybridRetrievalConfigDTO payload = new HybridRetrievalConfigDTO();
        payload.setEnabled(true);
        payload.setFusionMode("linear");
        payload.setFileVecEnabled(true);
        payload.setFileVecK(8);
        HybridRetrievalConfigDTO out = service.updateConfig(payload);
        assertThat(out.getFusionMode()).isEqualTo("LINEAR");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(appSettingsService).upsertString(eq(HybridRetrievalConfigService.KEY_CONFIG_JSON), jsonCaptor.capture());
        verify(appSettingsService).upsertString(eq(HybridRetrievalConfigService.KEY_CONFIG_JSON_LEGACY), eq(jsonCaptor.getValue()));
        HybridRetrievalConfigDTO persisted = objectMapper.readValue(jsonCaptor.getValue(), HybridRetrievalConfigDTO.class);
        assertThat(persisted.getFusionMode()).isEqualTo("LINEAR");

        doThrow(new RuntimeException("db")).when(appSettingsService).upsertString(eq(HybridRetrievalConfigService.KEY_CONFIG_JSON), eq(jsonCaptor.getValue()));
        assertThatThrownBy(() -> service.updateConfig(payload)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getConfig_shouldUseLegacyKeyWhenPrimaryBlank() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        HybridRetrievalConfigService service = new HybridRetrievalConfigService(appSettingsService, objectMapper);

        when(appSettingsService.getString(HybridRetrievalConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of(" "));
        when(appSettingsService.getString(HybridRetrievalConfigService.KEY_CONFIG_JSON_LEGACY))
                .thenReturn(Optional.of("{\"fusionMode\":\"linear\",\"enabled\":true}"));

        HybridRetrievalConfigDTO out = service.getConfig();
        assertThat(out.getFusionMode()).isEqualTo("LINEAR");
        assertThat(out.getEnabled()).isTrue();

        HybridRetrievalConfigDTO nullPath = service.normalizeConfig(null);
        assertThat(nullPath).isNotNull();
        assertThat(nullPath.getFusionMode()).isEqualTo("RRF");
    }

    @Test
    void getConfig_shouldParsePrimaryJsonBranch() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        HybridRetrievalConfigService service = new HybridRetrievalConfigService(appSettingsService, new ObjectMapper());
        when(appSettingsService.getString(HybridRetrievalConfigService.KEY_CONFIG_JSON))
                .thenReturn(Optional.of("{\"fusionMode\":\"linear\",\"enabled\":false,\"fileVecEnabled\":false}"));

        HybridRetrievalConfigDTO out = service.getConfig();
        assertThat(out.getFusionMode()).isEqualTo("LINEAR");
        assertThat(out.getEnabled()).isFalse();
        assertThat(out.getFileVecEnabled()).isFalse();
    }

    @Test
    void normalizeConfig_shouldKeepRerankKWhenDisabledAndHybridKWithinMaxDocs() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        HybridRetrievalConfigService service = new HybridRetrievalConfigService(appSettingsService, new ObjectMapper());

        HybridRetrievalConfigDTO payload = new HybridRetrievalConfigDTO();
        payload.setEnabled(true);
        payload.setFileVecEnabled(true);
        payload.setFileVecK(40);
        payload.setFusionMode("linear");
        payload.setRerankEnabled(false);
        payload.setRerankModel("  m2  ");
        payload.setRerankK(400);
        payload.setMaxDocs(300);
        payload.setHybridK(200);

        HybridRetrievalConfigDTO out = service.normalizeConfig(payload);
        assertThat(out.getEnabled()).isTrue();
        assertThat(out.getFileVecEnabled()).isTrue();
        assertThat(out.getFileVecK()).isEqualTo(40);
        assertThat(out.getFusionMode()).isEqualTo("LINEAR");
        assertThat(out.getRerankEnabled()).isFalse();
        assertThat(out.getRerankModel()).isEqualTo("m2");
        assertThat(out.getRerankK()).isEqualTo(400);
        assertThat(out.getHybridK()).isEqualTo(200);
    }

    @Test
    void getConfig_shouldFallbackWhenJsonLiteralNull() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        HybridRetrievalConfigService service = new HybridRetrievalConfigService(appSettingsService, new ObjectMapper());
        when(appSettingsService.getString(HybridRetrievalConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of("null"));

        HybridRetrievalConfigDTO out = service.getConfig();
        assertThat(out.getFusionMode()).isEqualTo("RRF");
        assertThat(out.getMaxDocs()).isEqualTo(500);
    }
}
