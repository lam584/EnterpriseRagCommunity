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

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AiProvidersConfigServiceBranch100Test {

    @Test
    void updateAdminConfigSingleArgDelegatesAndWritesNullActor() {
        TestDeps d = new TestDeps();
        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(), List.of());
        when(d.llmProviderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmProviderSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmProviderRepository.findByEnvAndEnabledTrueOrderByPriorityAscIdAsc("default")).thenReturn(List.of());
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);

        AiProviderDTO p = new AiProviderDTO();
        p.setId("p1");
        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        payload.setProviders(List.of(p));
        payload.setActiveProviderId("missing");

        d.svc.updateAdminConfig(payload);

        ArgumentCaptor<LlmProviderSettingsEntity> settingsCap = ArgumentCaptor.forClass(LlmProviderSettingsEntity.class);
        verify(d.llmProviderSettingsRepository).save(settingsCap.capture());
        assertNull(settingsCap.getValue().getUpdatedBy());
    }

    @Test
    void updateAdminConfigWithConfiguredCryptoClearsEncryptedHeadersWhenMergedEmpty() {
        TestDeps d = new TestDeps();
        LlmProviderEntity existing = provider("p1");
        existing.setExtraHeadersEncrypted(new byte[]{1});

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(existing), List.of(existing));
        when(d.llmProviderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmProviderSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(true);
        when(d.llmSecretsCryptoService.decryptHeadersOrEmpty(any())).thenReturn(Map.of());
        when(d.llmProviderRepository.findByEnvAndEnabledTrueOrderByPriorityAscIdAsc("default")).thenReturn(List.of(existing));

        AiProviderDTO p = new AiProviderDTO();
        p.setId("p1");
        p.setExtraHeaders(new LinkedHashMap<>(Map.of("k", "******")));
        AiProvidersConfigDTO payload = new AiProvidersConfigDTO();
        payload.setProviders(List.of(p));
        payload.setActiveProviderId("p1");

        d.svc.updateAdminConfig(payload, 1L);

        ArgumentCaptor<Iterable<LlmProviderEntity>> cap = ArgumentCaptor.forClass(Iterable.class);
        verify(d.llmProviderRepository).saveAll(cap.capture());
        LlmProviderEntity saved = cap.getValue().iterator().next();
        assertNull(saved.getExtraHeadersEncrypted());
        verify(d.llmSecretsCryptoService, never()).encryptHeadersOrNull(any());
    }

    @Test
    void resolveProviderUsesDefaultApiKeyAndIgnoresEncryptedHeadersWhenCryptoDisabled() {
        TestDeps d = new TestDeps();
        LlmProviderEntity p = provider("p1");
        p.setApiKeyEncrypted(new byte[]{7});
        p.setExtraHeadersEncrypted(new byte[]{9});
        p.setBaseUrl("https://p1");
        p.setDefaultChatModel("m1");

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(p));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);
        when(d.systemConfigurationService.getConfig("APP_AI_API_KEY")).thenReturn("fallback-key");

        AiProvidersConfigService.ResolvedProvider out = d.svc.resolveProvider("p1");
        assertEquals("fallback-key", out.apiKey());
        assertEquals(Map.of(), out.extraHeaders());
        verify(d.llmSecretsCryptoService, never()).decryptStringOrNull(any());
        verify(d.llmSecretsCryptoService, never()).decryptHeadersOrEmpty(any());
    }

    @Test
    void normalizeAndMaskSecretsCoverNullInputAndNullProviderEntries() throws Exception {
        Method normalize = AiProvidersConfigService.class.getDeclaredMethod("normalize", AiProvidersConfigDTO.class);
        normalize.setAccessible(true);
        Method maskSecrets = AiProvidersConfigService.class.getDeclaredMethod("maskSecrets", AiProvidersConfigDTO.class);
        maskSecrets.setAccessible(true);

        TestDeps d = new TestDeps();

        AiProvidersConfigDTO normalized = (AiProvidersConfigDTO) normalize.invoke(d.svc, new Object[]{null});
        assertEquals(List.of(), normalized.getProviders());
        assertNull(normalized.getActiveProviderId());

        assertNull(maskSecrets.invoke(null, new Object[]{null}));

        AiProvidersConfigDTO in = new AiProvidersConfigDTO();
        in.setActiveProviderId("p1");
        in.setProviders(List.<AiProviderDTO>of());
        AiProvidersConfigDTO masked = (AiProvidersConfigDTO) maskSecrets.invoke(null, in);
        assertEquals("p1", masked.getActiveProviderId());
        assertTrue(masked.getProviders().isEmpty());
    }

    @Test
    void loadLegacyOrDefaultFallsBackToDefaultWhenSettingMissing() throws Exception {
        TestDeps d = new TestDeps();
        when(d.appSettingsService.getString(AiProvidersConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.empty());

        Method m = AiProvidersConfigService.class.getDeclaredMethod("loadLegacyOrDefault");
        m.setAccessible(true);
        AiProvidersConfigDTO out = (AiProvidersConfigDTO) m.invoke(d.svc);

        assertEquals("default", out.getActiveProviderId());
        assertEquals(1, out.getProviders().size());
        assertEquals("default", out.getProviders().get(0).getId());
    }

    @Test
    void upsertSettingsUpdatesExistingEntity() throws Exception {
        TestDeps d = new TestDeps();
        LlmProviderSettingsEntity existing = new LlmProviderSettingsEntity();
        existing.setEnv("default");
        existing.setActiveProviderId("old");
        existing.setVersion(3);

        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.of(existing));
        when(d.llmProviderSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Method m = AiProvidersConfigService.class.getDeclaredMethod("upsertSettings", String.class, String.class, LocalDateTime.class, Long.class);
        m.setAccessible(true);
        LocalDateTime now = LocalDateTime.now();
        m.invoke(d.svc, "default", "new", now, 11L);

        ArgumentCaptor<LlmProviderSettingsEntity> cap = ArgumentCaptor.forClass(LlmProviderSettingsEntity.class);
        verify(d.llmProviderSettingsRepository).save(cap.capture());
        assertEquals("default", cap.getValue().getEnv());
        assertEquals("new", cap.getValue().getActiveProviderId());
        assertEquals(Integer.valueOf(3), cap.getValue().getVersion());
    }

    private static LlmProviderEntity provider(String id) {
        LlmProviderEntity e = new LlmProviderEntity();
        e.setEnv("default");
        e.setProviderId(id);
        e.setType("OPENAI_COMPAT");
        e.setEnabled(true);
        return e;
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
        }
    }
}
