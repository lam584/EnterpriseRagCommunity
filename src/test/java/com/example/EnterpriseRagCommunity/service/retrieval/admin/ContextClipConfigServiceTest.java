package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
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

    @Test
    void getConfig_shouldFallbackForBlankAndInvalidJson() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ContextClipConfigService service = new ContextClipConfigService(appSettingsService, objectMapper);
        when(appSettingsService.getString(ContextClipConfigService.KEY_CONFIG_JSON))
                .thenReturn(Optional.of("   "), Optional.of("{bad"));

        ContextClipConfigDTO cfgBlank = service.getConfig();
        ContextClipConfigDTO cfgBad = service.getConfigOrDefault();
        assertEquals(3000, cfgBlank.getContextTokenBudget());
        assertEquals(ContextClipConfigService.ABLATION_REL_IMP_RED, cfgBad.getAblationMode());
    }

    @Test
    void normalizeConfig_shouldCoverExtraBranches() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ContextClipConfigService service = new ContextClipConfigService(appSettingsService, new ObjectMapper());

        ContextClipConfigDTO payload = new ContextClipConfigDTO();
        payload.setEnabled(false);
        payload.setPolicy(null);
        payload.setContextTokenBudget(null);
        payload.setMaxContextTokens(80);
        payload.setReserveAnswerTokens(-1);
        payload.setPerItemMaxTokens(1);
        payload.setMaxPromptChars(9);
        payload.setMinScore(Double.NaN);
        payload.setMaxSamePostItems(-5);
        payload.setRequireTitle(null);
        payload.setAlpha(Double.POSITIVE_INFINITY);
        payload.setBeta(-1.0);
        payload.setGamma(99.0);
        payload.setAblationMode("relative+importance");
        payload.setCrossSourceDedup(null);
        payload.setDedupByPostId(null);
        payload.setDedupByTitle(null);
        payload.setDedupByContentHash(null);
        payload.setSectionTitle(" ");
        payload.setItemHeaderTemplate(null);
        payload.setSeparator(null);
        payload.setShowPostId(null);
        payload.setShowChunkIndex(null);
        payload.setShowScore(null);
        payload.setShowTitle(null);
        payload.setExtraInstruction("  ");
        payload.setLogEnabled(null);
        payload.setLogSampleRate(-2.0);
        payload.setLogMaxDays(99999);

        ContextClipConfigDTO out = service.normalizeConfig(payload);
        assertFalse(Boolean.TRUE.equals(out.getEnabled()));
        assertEquals(100, out.getContextTokenBudget());
        assertEquals(100, out.getMaxContextTokens());
        assertEquals(0, out.getReserveAnswerTokens());
        assertEquals(50, out.getPerItemMaxTokens());
        assertEquals(1000, out.getMaxPromptChars());
        assertNull(out.getMinScore());
        assertEquals(0, out.getMaxSamePostItems());
        assertEquals(1.0, out.getAlpha());
        assertEquals(0.0, out.getBeta());
        assertEquals(10.0, out.getGamma());
        assertEquals(ContextClipConfigService.ABLATION_REL_IMP, out.getAblationMode());
        assertEquals("\n\n", out.getSeparator());
        assertNull(out.getExtraInstruction());
        assertEquals(0.0, out.getLogSampleRate());
        assertEquals(3650, out.getLogMaxDays());
    }

    @Test
    void updateConfig_shouldThrowForNullPayloadAndPersistFailure() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ContextClipConfigService service = new ContextClipConfigService(appSettingsService, objectMapper);

        assertThrows(IllegalArgumentException.class, () -> service.updateConfig(null));

        doThrow(new RuntimeException("db")).when(appSettingsService)
                .upsertString(org.mockito.ArgumentMatchers.eq(ContextClipConfigService.KEY_CONFIG_JSON), org.mockito.ArgumentMatchers.anyString());
        assertThrows(IllegalStateException.class, () -> service.updateConfig(new ContextClipConfigDTO()));
    }

    @Test
    void normalizeConfig_shouldCoverNullAndFiniteClampDoubleBranches() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ContextClipConfigService service = new ContextClipConfigService(appSettingsService, new ObjectMapper());

        ContextClipConfigDTO in = new ContextClipConfigDTO();
        in.setAlpha(null);
        in.setBeta(0.5);
        in.setGamma(2.0);
        in.setLogSampleRate(Double.POSITIVE_INFINITY);
        ContextClipConfigDTO out = service.normalizeConfig(in);
        assertEquals(1.0, out.getAlpha());
        assertEquals(0.5, out.getBeta());
        assertEquals(2.0, out.getGamma());
        assertEquals(1.0, out.getLogSampleRate());

        ContextClipConfigDTO nullPath = service.normalizeConfig(null);
        assertEquals(ContextClipConfigService.ABLATION_REL_IMP_RED, nullPath.getAblationMode());
    }

    @Test
    void privateHelpers_shouldCoverRemainingBranches() throws Exception {
        Method clampNullable = ContextClipConfigService.class
                .getDeclaredMethod("clampDoubleNullable", Double.class, double.class, double.class);
        clampNullable.setAccessible(true);
        assertNull(clampNullable.invoke(null, null, -1.0, 1.0));
        assertNull(clampNullable.invoke(null, Double.NaN, -1.0, 1.0));
        assertEquals(-1.0, (Double) clampNullable.invoke(null, -2.0, -1.0, 1.0));
        assertEquals(1.0, (Double) clampNullable.invoke(null, 2.0, -1.0, 1.0));

        Method clampDouble = ContextClipConfigService.class
                .getDeclaredMethod("clampDouble", Double.class, double.class, double.class, double.class);
        clampDouble.setAccessible(true);
        assertEquals(0.5, (Double) clampDouble.invoke(null, null, 0.0, 1.0, 0.5));
        assertEquals(0.5, (Double) clampDouble.invoke(null, Double.NaN, 0.0, 1.0, 0.5));
        assertEquals(0.0, (Double) clampDouble.invoke(null, -2.0, 0.0, 1.0, 0.5));
        assertEquals(1.0, (Double) clampDouble.invoke(null, 2.0, 0.0, 1.0, 0.5));

        Method mode = ContextClipConfigService.class.getDeclaredMethod("normalizeAblationMode", String.class, String.class);
        mode.setAccessible(true);
        assertEquals("DEF", mode.invoke(null, null, "DEF"));
        assertEquals(ContextClipConfigService.ABLATION_NONE, mode.invoke(null, "NO_PRUNING", "DEF"));
        assertEquals(ContextClipConfigService.ABLATION_REL_ONLY, mode.invoke(null, "REL_ONLY", "DEF"));
        assertEquals(ContextClipConfigService.ABLATION_REL_IMP, mode.invoke(null, "REL_IMP", "DEF"));
        assertEquals(ContextClipConfigService.ABLATION_REL_IMP_RED, mode.invoke(null, "REL-IMP+RED", "DEF"));
        assertEquals("DEF", mode.invoke(null, "unknown", "DEF"));
    }
}
