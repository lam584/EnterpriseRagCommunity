package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextClipConfigServiceTest {

    @Test
    void defaultConfig_shouldIncludeDynamicPruningFields() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ContextClipConfigService service = new ContextClipConfigService(appSettingsService, new ObjectMapper());

        ContextClipConfigDTO cfg = service.defaultConfig();
        assertEquals(3000, cfg.getContextTokenBudget());
        assertEquals(1.0, cfg.getAlpha());
        assertEquals(1.0, cfg.getBeta());
        assertEquals(1.0, cfg.getGamma());
        assertEquals(ContextClipConfigService.ABLATION_REL_IMP_RED, cfg.getAblationMode());
        assertTrue(Boolean.TRUE.equals(cfg.getCrossSourceDedup()));
    }

    @Test
    void normalizeConfig_shouldBackfillLegacyBudgetAndNormalizeMode() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ContextClipConfigService service = new ContextClipConfigService(appSettingsService, new ObjectMapper());

        ContextClipConfigDTO payload = new ContextClipConfigDTO();
        payload.setMaxContextTokens(4800);
        payload.setAblationMode("rel+imp-red");
        payload.setAlpha(-2.0);
        payload.setBeta(100.0);
        payload.setGamma(null);
        payload.setCrossSourceDedup(null);

        ContextClipConfigDTO out = service.normalizeConfig(payload);
        assertEquals(4800, out.getContextTokenBudget());
        assertEquals(4800, out.getMaxContextTokens());
        assertEquals(ContextClipConfigService.ABLATION_REL_IMP_RED, out.getAblationMode());
        assertEquals(0.0, out.getAlpha());
        assertEquals(10.0, out.getBeta());
        assertEquals(1.0, out.getGamma());
        assertTrue(Boolean.TRUE.equals(out.getCrossSourceDedup()));
    }

    @Test
    void getConfig_shouldNormalizeStoredLegacyJson() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ContextClipConfigService service = new ContextClipConfigService(appSettingsService, objectMapper);
        when(appSettingsService.getString(ContextClipConfigService.KEY_CONFIG_JSON))
                .thenReturn(Optional.of("""
                        {"maxContextTokens":4200,"ablationMode":"bad","crossSourceDedup":false}
                        """));

        ContextClipConfigDTO cfg = service.getConfig();
        assertEquals(4200, cfg.getContextTokenBudget());
        assertEquals(4200, cfg.getMaxContextTokens());
        assertEquals(ContextClipConfigService.ABLATION_REL_IMP_RED, cfg.getAblationMode());
        assertFalse(Boolean.TRUE.equals(cfg.getCrossSourceDedup()));
    }

    @Test
    void updateConfig_shouldPersistNormalizedJson() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ContextClipConfigService service = new ContextClipConfigService(appSettingsService, objectMapper);

        ContextClipConfigDTO payload = new ContextClipConfigDTO();
        payload.setContextTokenBudget(3500);
        payload.setAblationMode("REL");
        payload.setAlpha(0.6);
        payload.setBeta(0.2);
        payload.setGamma(0.1);

        ContextClipConfigDTO saved = service.updateConfig(payload);
        assertEquals(3500, saved.getContextTokenBudget());
        assertEquals(ContextClipConfigService.ABLATION_REL_ONLY, saved.getAblationMode());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(appSettingsService).upsertString(
                org.mockito.ArgumentMatchers.eq(ContextClipConfigService.KEY_CONFIG_JSON),
                captor.capture()
        );
        ContextClipConfigDTO persisted = objectMapper.readValue(captor.getValue(), ContextClipConfigDTO.class);
        assertEquals(3500, persisted.getContextTokenBudget());
        assertEquals(ContextClipConfigService.ABLATION_REL_ONLY, persisted.getAblationMode());
    }
}
