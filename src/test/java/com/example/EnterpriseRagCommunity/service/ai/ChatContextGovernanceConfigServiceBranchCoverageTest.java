package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static com.example.EnterpriseRagCommunity.service.ai.ChatContextGovernanceConfigService.KEY_CONFIG_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatContextGovernanceConfigServiceBranchCoverageTest {

    @Test
    void defaultConfigReturnsExpectedDefaults() {
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(
                mock(AppSettingsService.class),
                mock(ObjectMapper.class)
        );

        ChatContextGovernanceConfigDTO dto = service.defaultConfig();

        assertNotNull(dto);
        assertEquals(true, dto.getEnabled());
        assertEquals(24000, dto.getMaxPromptTokens());
        assertEquals(2000, dto.getReserveAnswerTokens());
        assertEquals(200000, dto.getMaxPromptChars());
        assertEquals(6000, dto.getPerMessageMaxTokens());
        assertEquals(20, dto.getKeepLastMessages());
        assertEquals(true, dto.getAllowDropRagContext());
        assertEquals(true, dto.getCompressionEnabled());
        assertEquals(18000, dto.getCompressionTriggerTokens());
        assertEquals(8, dto.getCompressionKeepLastMessages());
        assertEquals(300, dto.getCompressionPerMessageSnippetChars());
        assertEquals(12000, dto.getCompressionMaxChars());
        assertEquals(10, dto.getMaxFiles());
        assertEquals(6000, dto.getPerFileMaxChars());
        assertEquals(24000, dto.getTotalFilesMaxChars());
        assertEquals(true, dto.getLogEnabled());
        assertEquals(1.0, dto.getLogSampleRate());
        assertEquals(30, dto.getLogMaxDays());
    }

    @Test
    void getConfigReturnsDefaultWhenMissing() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(appSettingsService, objectMapper);

        when(appSettingsService.getString(KEY_CONFIG_JSON)).thenReturn(Optional.empty());

        ChatContextGovernanceConfigDTO dto = service.getConfig();
        ChatContextGovernanceConfigDTO dto2 = service.getConfigOrDefault();

        assertEquals(service.defaultConfig(), dto);
        assertEquals(service.defaultConfig(), dto2);
        verifyNoInteractions(objectMapper);
    }

    @Test
    void getConfigReturnsDefaultWhenBlank() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(appSettingsService, objectMapper);

        when(appSettingsService.getString(KEY_CONFIG_JSON)).thenReturn(Optional.of("   "));

        ChatContextGovernanceConfigDTO dto = service.getConfig();

        assertEquals(service.defaultConfig(), dto);
        verifyNoInteractions(objectMapper);
    }

    @Test
    void getConfigReturnsDefaultWhenJsonInvalid() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(appSettingsService, objectMapper);

        when(appSettingsService.getString(KEY_CONFIG_JSON)).thenReturn(Optional.of("{bad"));
        when(objectMapper.readValue(eq("{bad"), eq(ChatContextGovernanceConfigDTO.class)))
                .thenThrow(new RuntimeException("boom"));

        ChatContextGovernanceConfigDTO dto = service.getConfig();

        assertEquals(service.defaultConfig(), dto);
    }

    @Test
    void getConfigParsesAndNormalizesValues() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(appSettingsService, objectMapper);

        ChatContextGovernanceConfigDTO parsed = new ChatContextGovernanceConfigDTO();
        parsed.setEnabled(false);
        parsed.setMaxPromptTokens(1);
        parsed.setReserveAnswerTokens(null);
        parsed.setMaxPromptChars(3_000_000);
        parsed.setPerMessageMaxTokens(50);
        parsed.setKeepLastMessages(999);
        parsed.setAllowDropRagContext(null);
        parsed.setCompressionEnabled(false);
        parsed.setCompressionTriggerTokens(0);
        parsed.setCompressionKeepLastMessages(null);
        parsed.setCompressionPerMessageSnippetChars(9);
        parsed.setCompressionMaxChars(999_999);
        parsed.setMaxFiles(-1);
        parsed.setPerFileMaxChars(null);
        parsed.setTotalFilesMaxChars(50);
        parsed.setLogEnabled(null);
        parsed.setLogSampleRate(Double.POSITIVE_INFINITY);
        parsed.setLogMaxDays(0);

        when(appSettingsService.getString(KEY_CONFIG_JSON)).thenReturn(Optional.of("{json}"));
        when(objectMapper.readValue(eq("{json}"), eq(ChatContextGovernanceConfigDTO.class))).thenReturn(parsed);

        ChatContextGovernanceConfigDTO dto = service.getConfig();

        assertEquals(false, dto.getEnabled());
        assertEquals(1000, dto.getMaxPromptTokens());
        assertEquals(2000, dto.getReserveAnswerTokens());
        assertEquals(2_000_000, dto.getMaxPromptChars());
        assertEquals(100, dto.getPerMessageMaxTokens());
        assertEquals(200, dto.getKeepLastMessages());
        assertEquals(true, dto.getAllowDropRagContext());
        assertEquals(false, dto.getCompressionEnabled());
        assertEquals(500, dto.getCompressionTriggerTokens());
        assertEquals(8, dto.getCompressionKeepLastMessages());
        assertEquals(10, dto.getCompressionPerMessageSnippetChars());
        assertEquals(200000, dto.getCompressionMaxChars());
        assertEquals(0, dto.getMaxFiles());
        assertEquals(6000, dto.getPerFileMaxChars());
        assertEquals(100, dto.getTotalFilesMaxChars());
        assertEquals(true, dto.getLogEnabled());
        assertEquals(1.0, dto.getLogSampleRate());
        assertEquals(1, dto.getLogMaxDays());
    }

    @Test
    void getConfigParsesNullConfigAndNormalizesToDefaults() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(appSettingsService, objectMapper);

        when(appSettingsService.getString(KEY_CONFIG_JSON)).thenReturn(Optional.of("{json}"));
        when(objectMapper.readValue(eq("{json}"), eq(ChatContextGovernanceConfigDTO.class))).thenReturn(null);

        ChatContextGovernanceConfigDTO dto = service.getConfig();

        assertEquals(service.defaultConfig(), dto);
    }

    @Test
    void updateConfigRejectsNullPayload() {
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(
                mock(AppSettingsService.class),
                mock(ObjectMapper.class)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.updateConfig(null));
        assertEquals("payload is required", ex.getMessage());
    }

    @Test
    void updateConfigNormalizesAndSaves() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(appSettingsService, objectMapper);

        ChatContextGovernanceConfigDTO payload = new ChatContextGovernanceConfigDTO();
        payload.setEnabled(null);
        payload.setAllowDropRagContext(false);
        payload.setCompressionEnabled(null);
        payload.setKeepLastMessages(-1);
        payload.setLogSampleRate(-0.1);
        payload.setLogMaxDays(99999);

        when(objectMapper.writeValueAsString(any(ChatContextGovernanceConfigDTO.class))).thenReturn("{saved}");
        doNothing().when(appSettingsService).upsertString(eq(KEY_CONFIG_JSON), eq("{saved}"));

        ChatContextGovernanceConfigDTO out = service.updateConfig(payload);

        assertSame(payload, out);
        assertEquals(true, out.getEnabled());
        assertEquals(false, out.getAllowDropRagContext());
        assertEquals(true, out.getCompressionEnabled());
        assertEquals(0, out.getKeepLastMessages());
        assertEquals(0.0, out.getLogSampleRate());
        assertEquals(3650, out.getLogMaxDays());

        ArgumentCaptor<ChatContextGovernanceConfigDTO> cfgCaptor = ArgumentCaptor.forClass(ChatContextGovernanceConfigDTO.class);
        verify(objectMapper).writeValueAsString(cfgCaptor.capture());
        ChatContextGovernanceConfigDTO normalized = cfgCaptor.getValue();
        assertEquals(0, normalized.getKeepLastMessages());
        assertEquals(0.0, normalized.getLogSampleRate());

        verify(appSettingsService).upsertString(KEY_CONFIG_JSON, "{saved}");
    }

    @Test
    void updateConfigKeepsExplicitTrueFlags() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(appSettingsService, objectMapper);

        ChatContextGovernanceConfigDTO payload = new ChatContextGovernanceConfigDTO();
        payload.setEnabled(true);
        payload.setAllowDropRagContext(true);
        payload.setCompressionEnabled(true);
        payload.setLogEnabled(true);

        when(objectMapper.writeValueAsString(any(ChatContextGovernanceConfigDTO.class))).thenReturn("{saved}");
        doNothing().when(appSettingsService).upsertString(eq(KEY_CONFIG_JSON), eq("{saved}"));

        ChatContextGovernanceConfigDTO out = service.updateConfig(payload);

        assertEquals(true, out.getEnabled());
        assertEquals(true, out.getAllowDropRagContext());
        assertEquals(true, out.getCompressionEnabled());
        assertEquals(true, out.getLogEnabled());
    }

    @Test
    void updateConfigClampsLogSampleRateAboveMax() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(appSettingsService, objectMapper);

        ChatContextGovernanceConfigDTO payload = new ChatContextGovernanceConfigDTO();
        payload.setLogEnabled(false);
        payload.setLogSampleRate(1.1);

        when(objectMapper.writeValueAsString(any(ChatContextGovernanceConfigDTO.class))).thenReturn("{saved}");
        doNothing().when(appSettingsService).upsertString(eq(KEY_CONFIG_JSON), eq("{saved}"));

        ChatContextGovernanceConfigDTO out = service.updateConfig(payload);

        assertEquals(false, out.getLogEnabled());
        assertEquals(1.0, out.getLogSampleRate());
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.NEGATIVE_INFINITY})
    void updateConfigFallsBackLogSampleRateWhenNaNOrInfinite(double v) throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(appSettingsService, objectMapper);

        ChatContextGovernanceConfigDTO payload = new ChatContextGovernanceConfigDTO();
        payload.setLogSampleRate(v);

        when(objectMapper.writeValueAsString(any(ChatContextGovernanceConfigDTO.class))).thenReturn("{saved}");
        doNothing().when(appSettingsService).upsertString(eq(KEY_CONFIG_JSON), eq("{saved}"));

        ChatContextGovernanceConfigDTO out = service.updateConfig(payload);

        assertEquals(1.0, out.getLogSampleRate());
    }

    @Test
    void updateConfigThrowsWhenSerializeFails() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(appSettingsService, objectMapper);

        when(objectMapper.writeValueAsString(any(ChatContextGovernanceConfigDTO.class)))
                .thenThrow(new RuntimeException("boom"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.updateConfig(new ChatContextGovernanceConfigDTO()));
        assertEquals("保存配置失败: boom", ex.getMessage());
        verify(appSettingsService, never()).upsertString(any(), any());
    }

    @Test
    void updateConfigThrowsWhenUpsertFails() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ChatContextGovernanceConfigService service = new ChatContextGovernanceConfigService(appSettingsService, objectMapper);

        when(objectMapper.writeValueAsString(any(ChatContextGovernanceConfigDTO.class))).thenReturn("{saved}");
        doThrow(new RuntimeException("db down")).when(appSettingsService).upsertString(eq(KEY_CONFIG_JSON), eq("{saved}"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.updateConfig(new ChatContextGovernanceConfigDTO()));
        assertEquals("保存配置失败: db down", ex.getMessage());
        assertNotNull(ex.getCause());
        assertEquals("db down", ex.getCause().getMessage());
    }
}
