package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadFormatsConfigDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadFormatsConfigServiceBranchCoverageTest {

    @Test
    void getConfig_shouldReturnDefault_whenJsonMissing() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UploadFormatsConfigService svc = new UploadFormatsConfigService(appSettingsService, objectMapper);

        when(appSettingsService.getString(UploadFormatsConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.empty());

        UploadFormatsConfigDTO cfg = svc.getConfig();
        assertNotNull(cfg);
        assertEquals(Boolean.TRUE, cfg.getEnabled());
        assertNotNull(cfg.getFormats());
        assertTrue(cfg.getFormats().size() > 0);
    }

    @Test
    void getConfig_shouldReturnDefault_whenJsonBlank() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UploadFormatsConfigService svc = new UploadFormatsConfigService(appSettingsService, objectMapper);

        when(appSettingsService.getString(UploadFormatsConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of("   "));

        UploadFormatsConfigDTO cfg = svc.getConfig();
        assertNotNull(cfg);
        assertEquals(Boolean.TRUE, cfg.getEnabled());
        assertNotNull(cfg.getFormats());
        assertTrue(cfg.getFormats().size() > 0);
    }

    @Test
    void getConfig_shouldReturnDefault_whenJsonInvalid() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UploadFormatsConfigService svc = new UploadFormatsConfigService(appSettingsService, objectMapper);

        when(appSettingsService.getString(UploadFormatsConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of("{bad"));

        UploadFormatsConfigDTO cfg = svc.getConfig();
        assertNotNull(cfg);
        assertEquals(Boolean.TRUE, cfg.getEnabled());
        assertNotNull(cfg.getFormats());
        assertTrue(cfg.getFormats().size() > 0);
    }

    @Test
    void getConfig_shouldNormalize_whenJsonNullLiteral() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UploadFormatsConfigService svc = new UploadFormatsConfigService(appSettingsService, objectMapper);

        when(appSettingsService.getString(UploadFormatsConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of("null"));

        UploadFormatsConfigDTO cfg = svc.getConfig();
        assertNotNull(cfg);
        assertEquals(Boolean.FALSE, cfg.getEnabled());
        assertNotNull(cfg.getFormats());
        assertEquals(0, cfg.getFormats().size());
        assertEquals(100000, cfg.getMaxFilesPerRequest());
    }

    @Test
    void getConfig_shouldParseAndNormalize_whenJsonValid() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UploadFormatsConfigService svc = new UploadFormatsConfigService(appSettingsService, objectMapper);

        UploadFormatsConfigDTO dto = new UploadFormatsConfigDTO();
        dto.setEnabled(Boolean.TRUE);
        dto.setMaxFilesPerRequest(0);
        dto.setMaxFileSizeBytes(Long.MAX_VALUE);
        dto.setMaxTotalSizeBytes(0L);
        dto.setParseTimeoutMillis(999_999_999);
        dto.setParseMaxChars(1L);

        List<UploadFormatsConfigDTO.UploadFormatRuleDTO> rules = new ArrayList<>();
        rules.add(null);

        UploadFormatsConfigDTO.UploadFormatRuleDTO blank = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
        blank.setFormat(" ");
        blank.setEnabled(true);
        blank.setExtensions(List.of("pdf"));
        rules.add(blank);

        UploadFormatsConfigDTO.UploadFormatRuleDTO r1 = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
        r1.setFormat(" pdf ");
        r1.setEnabled(null);
        r1.setParseEnabled(Boolean.TRUE);
        r1.setMaxFileSizeBytes(0L);
        r1.setExtensions(Arrays.asList(".PDF", "pdf", "a-b", "  ", null, "abcdefghijklmnopq"));
        rules.add(r1);

        UploadFormatsConfigDTO.UploadFormatRuleDTO r2 = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
        r2.setFormat("img");
        r2.setEnabled(true);
        r2.setParseEnabled(false);
        r2.setMaxFileSizeBytes(10L);
        r2.setExtensions(List.of("png", "png", ".JPG"));
        rules.add(r2);

        dto.setFormats(rules);

        when(appSettingsService.getString(UploadFormatsConfigService.KEY_CONFIG_JSON))
                .thenReturn(Optional.of(objectMapper.writeValueAsString(dto)));

        UploadFormatsConfigDTO cfg = svc.getConfig();
        assertNotNull(cfg);
        assertEquals(Boolean.TRUE, cfg.getEnabled());
        assertEquals(1, cfg.getMaxFilesPerRequest());
        assertEquals(86_400_000, cfg.getParseTimeoutMillis());
        assertEquals(1000L, cfg.getParseMaxChars());

        assertNotNull(cfg.getFormats());
        assertEquals(2, cfg.getFormats().size());

        UploadFormatsConfigDTO.UploadFormatRuleDTO n1 = cfg.getFormats().get(0);
        assertEquals("PDF", n1.getFormat());
        assertEquals(Boolean.FALSE, n1.getEnabled());
        assertEquals(Boolean.TRUE, n1.getParseEnabled());
        assertNull(n1.getMaxFileSizeBytes());
        assertEquals(List.of("pdf"), n1.getExtensions());

        UploadFormatsConfigDTO.UploadFormatRuleDTO n2 = cfg.getFormats().get(1);
        assertEquals("IMG", n2.getFormat());
        assertEquals(Boolean.TRUE, n2.getEnabled());
        assertEquals(Boolean.FALSE, n2.getParseEnabled());
        assertEquals(10L, n2.getMaxFileSizeBytes());
        assertEquals(List.of("png", "jpg"), n2.getExtensions());
    }

    @Test
    void updateConfig_shouldThrow_whenPayloadNull() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UploadFormatsConfigService svc = new UploadFormatsConfigService(appSettingsService, objectMapper);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.updateConfig(null));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("payload"));
    }

    @Test
    void updateConfig_shouldPersistNormalizedJson_andClampValues() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UploadFormatsConfigService svc = new UploadFormatsConfigService(appSettingsService, objectMapper);

        UploadFormatsConfigDTO payload = new UploadFormatsConfigDTO();
        payload.setEnabled(null);
        payload.setMaxFilesPerRequest(null);
        payload.setParseTimeoutMillis(0);
        payload.setMaxFileSizeBytes(0L);
        payload.setMaxTotalSizeBytes(Long.MAX_VALUE);
        payload.setParseMaxChars(Long.MAX_VALUE);
        payload.setFormats(null);

        UploadFormatsConfigDTO savedCfg = svc.updateConfig(payload);
        assertNotNull(savedCfg);
        assertEquals(Boolean.FALSE, savedCfg.getEnabled());
        assertEquals(100000, savedCfg.getMaxFilesPerRequest());
        assertEquals(1000, savedCfg.getParseTimeoutMillis());
        assertEquals(1L, savedCfg.getMaxFileSizeBytes());

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCap = ArgumentCaptor.forClass(String.class);
        verify(appSettingsService).upsertString(keyCap.capture(), valueCap.capture());
        assertEquals(UploadFormatsConfigService.KEY_CONFIG_JSON, keyCap.getValue());

        UploadFormatsConfigDTO fromJson = objectMapper.readValue(valueCap.getValue(), UploadFormatsConfigDTO.class);
        assertNotNull(fromJson);
        assertEquals(UploadFormatsConfigService.KEY_CONFIG_JSON, keyCap.getValue());
        assertEquals(2L * 1024 * 1024 * 1024 * 1024, fromJson.getMaxTotalSizeBytes());
        assertEquals(10_000_000_000L, fromJson.getParseMaxChars());
        assertNotNull(fromJson.getFormats());
    }

    @Test
    void updateConfig_shouldKeepValuesWithinRange() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UploadFormatsConfigService svc = new UploadFormatsConfigService(appSettingsService, objectMapper);

        UploadFormatsConfigDTO payload = new UploadFormatsConfigDTO();
        payload.setEnabled(Boolean.TRUE);
        payload.setMaxFilesPerRequest(2);
        payload.setParseTimeoutMillis(2000);
        payload.setMaxFileSizeBytes(5L);
        payload.setMaxTotalSizeBytes(7L);
        payload.setParseMaxChars(3000L);
        payload.setFormats(List.of());

        UploadFormatsConfigDTO cfg = svc.updateConfig(payload);
        assertNotNull(cfg);
        assertEquals(Boolean.TRUE, cfg.getEnabled());
        assertEquals(2, cfg.getMaxFilesPerRequest());
        assertEquals(2000, cfg.getParseTimeoutMillis());
        assertEquals(5L, cfg.getMaxFileSizeBytes());
        assertEquals(7L, cfg.getMaxTotalSizeBytes());
        assertEquals(3000L, cfg.getParseMaxChars());
    }

    @Test
    void updateConfig_shouldWrapException_whenSerializeFails() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        UploadFormatsConfigService svc = new UploadFormatsConfigService(appSettingsService, objectMapper);

        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));

        UploadFormatsConfigDTO payload = UploadFormatsConfigDTO.empty();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.updateConfig(payload));
        assertNotNull(ex.getCause());
    }

    @Test
    void updateConfig_shouldWrapException_whenUpsertFails() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        UploadFormatsConfigService svc = new UploadFormatsConfigService(appSettingsService, objectMapper);

        doThrow(new RuntimeException("db")).when(appSettingsService).upsertString(eq(UploadFormatsConfigService.KEY_CONFIG_JSON), any());

        UploadFormatsConfigDTO payload = UploadFormatsConfigDTO.empty();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.updateConfig(payload));
        assertNotNull(ex.getCause());
    }

    @Test
    void enabledExtensionToRule_shouldReturnEmpty_whenDisabledOrFormatsNull() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        UploadFormatsConfigService disabled = new UploadFormatsConfigService(appSettingsService, objectMapper) {
            @Override
            public UploadFormatsConfigDTO getConfig() {
                UploadFormatsConfigDTO dto = new UploadFormatsConfigDTO();
                dto.setEnabled(false);
                dto.setFormats(List.of());
                return dto;
            }
        };
        assertTrue(disabled.enabledExtensionToRule().isEmpty());

        UploadFormatsConfigService formatsNull = new UploadFormatsConfigService(appSettingsService, objectMapper) {
            @Override
            public UploadFormatsConfigDTO getConfig() {
                UploadFormatsConfigDTO dto = new UploadFormatsConfigDTO();
                dto.setEnabled(true);
                dto.setFormats(null);
                return dto;
            }
        };
        assertTrue(formatsNull.enabledExtensionToRule().isEmpty());
    }

    @Test
    void enabledExtensionToRule_shouldSkipInvalid_andResolveConflicts() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        UploadFormatsConfigDTO.UploadFormatRuleDTO keep = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
        keep.setEnabled(true);
        keep.setExtensions(Arrays.asList(null, " ", ".PDF", "a-b", "docx", "abcdefghijklmnopq"));

        UploadFormatsConfigDTO.UploadFormatRuleDTO lose = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
        lose.setEnabled(true);
        lose.setExtensions(List.of("DOCX"));

        UploadFormatsConfigService svc = new UploadFormatsConfigService(appSettingsService, objectMapper) {
            @Override
            public UploadFormatsConfigDTO getConfig() {
                UploadFormatsConfigDTO dto = new UploadFormatsConfigDTO();
                dto.setEnabled(true);
                dto.setFormats(Arrays.asList(
                        null,
                        rule(null, List.of("pdf")),
                        rule(false, List.of("pdf")),
                        rule(true, null),
                        keep,
                        lose
                ));
                return dto;
            }

            private UploadFormatsConfigDTO.UploadFormatRuleDTO rule(Boolean enabled, List<String> exts) {
                UploadFormatsConfigDTO.UploadFormatRuleDTO r = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
                r.setEnabled(enabled);
                r.setExtensions(exts);
                return r;
            }
        };

        Map<String, UploadFormatsConfigDTO.UploadFormatRuleDTO> m = svc.enabledExtensionToRule();
        assertNotNull(m);
        assertEquals(2, m.size());
        assertSame(keep, m.get("pdf"));
        assertSame(keep, m.get("docx"));
    }

    @Test
    void normalizeRule_shouldCoverNullFormatAndOptionalFields_viaReflection() throws Exception {
        Method method = UploadFormatsConfigService.class.getDeclaredMethod(
                "normalizeRule",
                UploadFormatsConfigDTO.UploadFormatRuleDTO.class
        );
        method.setAccessible(true);

        UploadFormatsConfigDTO.UploadFormatRuleDTO nullFormat = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
        nullFormat.setFormat(null);
        nullFormat.setEnabled(Boolean.TRUE);
        nullFormat.setMaxFileSizeBytes(null);
        nullFormat.setExtensions(null);
        Object nullResult = method.invoke(null, nullFormat);
        assertNull(nullResult);

        UploadFormatsConfigDTO.UploadFormatRuleDTO minimal = new UploadFormatsConfigDTO.UploadFormatRuleDTO();
        minimal.setFormat(" txt ");
        minimal.setEnabled(Boolean.TRUE);
        minimal.setParseEnabled(null);
        minimal.setMaxFileSizeBytes(null);
        minimal.setExtensions(null);
        UploadFormatsConfigDTO.UploadFormatRuleDTO normalized =
                (UploadFormatsConfigDTO.UploadFormatRuleDTO) method.invoke(null, minimal);

        assertNotNull(normalized);
        assertEquals("TXT", normalized.getFormat());
        assertEquals(Boolean.TRUE, normalized.getEnabled());
        assertEquals(Boolean.FALSE, normalized.getParseEnabled());
        assertNull(normalized.getMaxFileSizeBytes());
        assertEquals(List.of(), normalized.getExtensions());
    }
}
