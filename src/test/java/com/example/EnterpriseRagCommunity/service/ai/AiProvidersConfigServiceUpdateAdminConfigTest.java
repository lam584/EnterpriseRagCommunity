package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiProviderDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiProvidersConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmProviderEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmProviderSettingsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderSettingsRepository;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AiProvidersConfigServiceUpdateAdminConfigTest {

    @Test
    void updateAdminConfigThrowsWhenPayloadNull() {
        TestDeps d = new TestDeps();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> d.svc.updateAdminConfig(null, 1L));
        assertTrue(ex.getMessage().contains("payload"));
    }

    @Test
    void updateAdminConfigSkipsNullAndBlankProvidersAndSetsDefaultsAndMetadata() {
        TestDeps d = new TestDeps();

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(), List.of());
        when(d.llmProviderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmProviderSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);

        AiProviderDTO blank = new AiProviderDTO();
        blank.setId("  ");

        AiProviderDTO p1 = new AiProviderDTO();
        p1.setId(" p1 ");
        p1.setType(null);
        p1.setEnabled(null);
        p1.setDefaultRerankModel(null);
        p1.setRerankEndpointPath(" /rerank ");
        p1.setSupportsVision(null);

        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        payload.setProviders(Arrays.asList(null, blank, p1));
        payload.setActiveProviderId("p1");

        d.svc.updateAdminConfig(payload, 7L);

        ArgumentCaptor<Iterable<LlmProviderEntity>> cap = ArgumentCaptor.forClass(Iterable.class);
        verify(d.llmProviderRepository).saveAll(cap.capture());

        List<LlmProviderEntity> saved = toList(cap.getValue());
        assertEquals(1, saved.size());
        LlmProviderEntity e = saved.get(0);
        assertEquals("default", e.getEnv());
        assertEquals("p1", e.getProviderId());
        assertEquals("OPENAI_COMPAT", e.getType());
        assertEquals(true, e.getEnabled());
        assertNotNull(e.getCreatedAt());
        assertNotNull(e.getUpdatedAt());
        assertEquals(7L, e.getCreatedBy());
        assertEquals(7L, e.getUpdatedBy());
        assertNotNull(e.getMetadata());
        assertEquals("/rerank", e.getMetadata().get("rerankEndpointPath"));
        assertEquals(null, e.getMetadata().get("supportsVision"));
        assertEquals(null, e.getMetadata().get("defaultRerankModel"));
    }

    @Test
    void updateAdminConfigApiKeyMaskKeepsOldAndEmptyClears() {
        TestDeps d = new TestDeps();

        LlmProviderEntity existing1 = provider("p1", new byte[]{1});
        LlmProviderEntity existing2 = provider("p2", new byte[]{2});

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(existing1, existing2), List.of(existing1, existing2));
        when(d.llmProviderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmProviderSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);

        AiProviderDTO p1 = new AiProviderDTO();
        p1.setId("p1");
        p1.setApiKey("******");

        AiProviderDTO p2 = new AiProviderDTO();
        p2.setId("p2");
        p2.setApiKey("  ");

        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        payload.setProviders(List.of(p1, p2));
        payload.setActiveProviderId("p1");

        d.svc.updateAdminConfig(payload, 7L);

        ArgumentCaptor<Iterable<LlmProviderEntity>> cap = ArgumentCaptor.forClass(Iterable.class);
        verify(d.llmProviderRepository).saveAll(cap.capture());
        List<LlmProviderEntity> saved = toList(cap.getValue());
        assertEquals(2, saved.size());

        LlmProviderEntity s1 = saved.stream().filter(x -> "p1".equals(x.getProviderId())).findFirst().orElseThrow();
        LlmProviderEntity s2 = saved.stream().filter(x -> "p2".equals(x.getProviderId())).findFirst().orElseThrow();
        assertEquals(1, s1.getApiKeyEncrypted()[0]);
        assertNull(s2.getApiKeyEncrypted());
        verify(d.llmSecretsCryptoService, never()).encryptStringOrNull(anyString());
    }

    @Test
    void updateAdminConfigThrowsWhenSavingRealApiKeyWithoutMasterKey() {
        TestDeps d = new TestDeps();

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of());
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);

        AiProviderDTO p1 = new AiProviderDTO();
        p1.setId("p1");
        p1.setApiKey("k");

        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        payload.setProviders(List.of(p1));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> d.svc.updateAdminConfig(payload, 1L));
        assertTrue(ex.getMessage().contains("apiKey"));
    }

    @Test
    void updateAdminConfigEncryptsApiKeyWhenMasterKeyConfigured() {
        TestDeps d = new TestDeps();

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(), List.of());
        when(d.llmProviderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmProviderSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(true);
        when(d.llmSecretsCryptoService.encryptStringOrNull("k-real")).thenReturn(new byte[]{7});

        AiProviderDTO p1 = new AiProviderDTO();
        p1.setId("p1");
        p1.setApiKey(" k-real ");
        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        payload.setProviders(List.of(p1));
        payload.setActiveProviderId("p1");

        d.svc.updateAdminConfig(payload, 1L);

        ArgumentCaptor<Iterable<LlmProviderEntity>> cap = ArgumentCaptor.forClass(Iterable.class);
        verify(d.llmProviderRepository).saveAll(cap.capture());
        LlmProviderEntity saved = toList(cap.getValue()).get(0);
        assertEquals(7, saved.getApiKeyEncrypted()[0]);
    }

    @Test
    void updateAdminConfigHeadersRealSecretRequiresMasterKey() {
        TestDeps d = new TestDeps();

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of());
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);

        AiProviderDTO p1 = new AiProviderDTO();
        p1.setId("p1");
        p1.setExtraHeaders(Map.of("k", "v"));

        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        payload.setProviders(List.of(p1));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> d.svc.updateAdminConfig(payload, 1L));
        assertTrue(ex.getMessage().contains("extraHeaders"));
    }

    @Test
    void updateAdminConfigHeadersMaskDoesNotRequireMasterKey() {
        TestDeps d = new TestDeps();

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(), List.of());
        when(d.llmProviderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmProviderSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);

        AiProviderDTO p1 = new AiProviderDTO();
        p1.setId("p1");
        p1.setExtraHeaders(new LinkedHashMap<>(Map.of("k", "******", "k2", "  ")));

        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        payload.setProviders(List.of(p1));

        d.svc.updateAdminConfig(payload, 1L);

        verify(d.llmSecretsCryptoService, never()).decryptHeadersOrEmpty(any());
        verify(d.llmSecretsCryptoService, never()).encryptHeadersOrNull(any());
    }

    @Test
    void updateAdminConfigHeadersMergeKeepsOldForMaskWhenConfigured() {
        TestDeps d = new TestDeps();

        LlmProviderEntity existing = provider("p1", null);
        existing.setExtraHeadersEncrypted(new byte[]{9});

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(existing), List.of(existing));
        when(d.llmProviderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmProviderSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(true);
        when(d.llmSecretsCryptoService.decryptHeadersOrEmpty(any())).thenReturn(Map.of("k1", "old1", "k2", "old2"));
        when(d.llmSecretsCryptoService.encryptHeadersOrNull(any())).thenReturn(new byte[]{1, 2});

        AiProviderDTO p1 = new AiProviderDTO();
        p1.setId("p1");
        p1.setExtraHeaders(new LinkedHashMap<>(Map.of("k1", "******", "k2", " new2 ")));

        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        payload.setProviders(List.of(p1));
        payload.setActiveProviderId("p1");

        d.svc.updateAdminConfig(payload, 1L);

        ArgumentCaptor<Iterable<LlmProviderEntity>> cap = ArgumentCaptor.forClass(Iterable.class);
        verify(d.llmProviderRepository).saveAll(cap.capture());
        LlmProviderEntity saved = toList(cap.getValue()).get(0);
        assertEquals(1, saved.getExtraHeadersEncrypted()[0]);
    }

    @Test
    void updateAdminConfigDeletesRemovedProvidersAndResetsRuntimeState() {
        TestDeps d = new TestDeps();

        LlmProviderEntity p1 = provider("p1", null);
        LlmProviderEntity p2 = provider("p2", null);

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(p1, p2), List.of(p1));
        when(d.llmProviderRepository.findByEnvAndEnabledTrueOrderByPriorityAscIdAsc("default")).thenReturn(List.of(p1));
        when(d.llmProviderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmProviderSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);

        AiProviderDTO next1 = new AiProviderDTO();
        next1.setId("p1");
        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        payload.setProviders(List.of(next1));
        payload.setActiveProviderId("missing");

        AiProvidersConfigDTO out = d.svc.updateAdminConfig(payload, 1L);
        assertNotNull(out);

        verify(d.llmModelRepository).deleteByEnvAndProviderId("default", "p2");
        verify(d.llmProviderRepository).deleteByEnvAndProviderId("default", "p2");
        verify(d.llmRoutingService).resetRuntimeState();
    }

    @Test
    void updateAdminConfigWrapsPersistLegacyMaskedCopyFailures() {
        TestDeps d = new TestDeps();

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(), List.of());
        when(d.llmProviderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmProviderSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmProviderRepository.findByEnvAndEnabledTrueOrderByPriorityAscIdAsc("default")).thenReturn(List.of());
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);
        doThrow(new RuntimeException("boom")).when(d.appSettingsService).upsertString(anyString(), anyString());

        AiProviderDTO p1 = new AiProviderDTO();
        p1.setId("p1");
        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        payload.setProviders(List.of(p1));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> d.svc.updateAdminConfig(payload, 1L));
        assertTrue(ex.getMessage().contains("写入兼容配置失败"));
        verify(d.llmRoutingService, never()).resetRuntimeState();
    }

    @Test
    void updateAdminConfigWithoutProvidersStillPersistsSettingsAndResetsRuntimeState() {
        TestDeps d = new TestDeps();

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(), List.of());
        when(d.llmProviderRepository.findByEnvAndEnabledTrueOrderByPriorityAscIdAsc("default")).thenReturn(List.of());
        when(d.llmProviderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmProviderSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);

        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        payload.setProviders(List.of());
        payload.setActiveProviderId("p-not-exists");

        d.svc.updateAdminConfig(payload, 9L);

        ArgumentCaptor<LlmProviderSettingsEntity> cap = ArgumentCaptor.forClass(LlmProviderSettingsEntity.class);
        verify(d.llmProviderSettingsRepository).save(cap.capture());
        assertNull(cap.getValue().getActiveProviderId());
        verify(d.llmRoutingService).resetRuntimeState();
    }

    private static LlmProviderEntity provider(String id, byte[] apiKeyEncrypted) {
        LlmProviderEntity e = new LlmProviderEntity();
        e.setEnv("default");
        e.setProviderId(id);
        e.setType("OPENAI_COMPAT");
        e.setEnabled(true);
        e.setPriority(0);
        e.setApiKeyEncrypted(apiKeyEncrypted);
        e.setUpdatedAt(java.time.LocalDateTime.now());
        e.setCreatedAt(java.time.LocalDateTime.now());
        return e;
    }

    private static List<LlmProviderEntity> toList(Iterable<LlmProviderEntity> it) {
        List<LlmProviderEntity> out = new ArrayList<>();
        for (LlmProviderEntity e : it) out.add(e);
        return out;
    }

    private static final class TestDeps {
        final AppSettingsService appSettingsService = mock(AppSettingsService.class);
        final ObjectMapper objectMapper = new ObjectMapper();
        final LlmProviderRepository llmProviderRepository = mock(LlmProviderRepository.class);
        final LlmProviderSettingsRepository llmProviderSettingsRepository = mock(LlmProviderSettingsRepository.class);
        final LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        final LlmSecretsCryptoService llmSecretsCryptoService = mock(LlmSecretsCryptoService.class);
        final SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        final LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);

        final AiProvidersConfigService svc = new AiProvidersConfigService(
                appSettingsService,
                objectMapper,
                llmProviderRepository,
                llmProviderSettingsRepository,
                llmModelRepository,
                llmSecretsCryptoService,
                systemConfigurationService,
                llmRoutingService
        );

        TestDeps() {
            when(appSettingsService.getString(anyString())).thenReturn(Optional.empty());
            when(systemConfigurationService.getConfig(anyString())).thenReturn(null);
            when(llmProviderSettingsRepository.save(any())).thenAnswer(inv -> {
                LlmProviderSettingsEntity s = inv.getArgument(0);
                return s;
            });
        }
    }
}
