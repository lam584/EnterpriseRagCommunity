package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.LlmProviderEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmProviderSettingsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderSettingsRepository;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AiProvidersConfigServiceResolveFromDbTest {

    @Test
    void resolveProviderPicksDesiredEvenIfDisabledAndAppliesDefaults() {
        TestDeps d = new TestDeps();

        LlmProviderEntity p1 = provider("p1", true);
        LlmProviderEntity p2 = provider("p2", false);
        p2.setType(null);
        p2.setBaseUrl(null);
        p2.setDefaultChatModel(null);
        p2.setApiKeyEncrypted(null);
        p2.setMetadata(Map.of("k", "v"));

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(p1, p2));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);
        when(d.systemConfigurationService.getConfig("APP_AI_BASE_URL")).thenReturn("https://base");
        when(d.systemConfigurationService.getConfig("APP_AI_API_KEY")).thenReturn("k0");
        when(d.systemConfigurationService.getConfig("APP_AI_MODEL")).thenReturn("m0");

        AiProvidersConfigService.ResolvedProvider res = d.svc.resolveProvider("p2");
        assertEquals("p2", res.id());
        assertEquals("OPENAI_COMPAT", res.type());
        assertEquals("https://base", res.baseUrl());
        assertEquals("k0", res.apiKey());
        assertEquals("m0", res.defaultChatModel());
        assertEquals(Map.of("k", "v"), res.metadata());
        assertEquals(Map.of(), res.extraHeaders());
        verify(d.systemConfigurationService, never()).getConfig("app.ai.base-url");
        verify(d.systemConfigurationService, never()).getConfig("app.ai.api-key");
        verify(d.systemConfigurationService, never()).getConfig("app.ai.model");
    }

    @Test
    void resolveActiveProviderFallsBackToSettingsActiveProviderId() {
        TestDeps d = new TestDeps();

        LlmProviderEntity p1 = provider("p1", false);
        p1.setBaseUrl("https://p1");
        p1.setType(" openai_compat ");

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(p1));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.of(settings("default", "p1")));
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);
        when(d.systemConfigurationService.getConfig(anyString())).thenReturn("x");

        AiProvidersConfigService.ResolvedProvider res = d.svc.resolveActiveProvider();
        assertEquals("p1", res.id());
        assertEquals("OPENAI_COMPAT", res.type());
        assertEquals("https://p1", res.baseUrl());
    }

    @Test
    void resolveProviderFallsBackToFirstEnabledWhenDesiredAndActiveMissing() {
        TestDeps d = new TestDeps();

        LlmProviderEntity p1 = provider("p1", false);
        LlmProviderEntity p2 = provider("p2", true);
        p2.setBaseUrl("https://p2");

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(p1, p2));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.of(settings("default", "missing")));
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);
        when(d.systemConfigurationService.getConfig(anyString())).thenReturn("x");

        AiProvidersConfigService.ResolvedProvider res = d.svc.resolveProvider(null);
        assertEquals("p2", res.id());
    }

    @Test
    void resolveProviderThrowsWhenNoDesiredAndAllDisabled() {
        TestDeps d = new TestDeps();

        LlmProviderEntity p1 = provider("p1", false);
        LlmProviderEntity p2 = provider("p2", false);

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(p1, p2));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> d.svc.resolveProvider(null));
    }

    @Test
    void resolveProviderWrapsApiKeyDecryptFailureWithHelpfulMessage() {
        TestDeps d = new TestDeps();

        LlmProviderEntity p = provider("p1", true);
        p.setApiKeyEncrypted(new byte[]{1, 2, 3});

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(p));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(true);
        when(d.llmSecretsCryptoService.decryptStringOrNull(any())).thenThrow(new IllegalArgumentException("bad"));
        when(d.systemConfigurationService.getConfig(anyString())).thenReturn("x");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> d.svc.resolveProvider(null));
        assertTrue(ex.getMessage().contains("apiKey 解密失败"));
        assertTrue(ex.getMessage().contains("providerId=p1"));
    }

    @Test
    void resolveProviderDecryptsAndNormalizesHeadersOnlyWhenConfigured() {
        TestDeps d = new TestDeps();

        LlmProviderEntity p = provider("p1", true);
        p.setExtraHeadersEncrypted(new byte[]{9});

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(p));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(true);
        when(d.llmSecretsCryptoService.decryptHeadersOrEmpty(any())).thenReturn(new LinkedHashMap<>(Map.of("k1", "v1", "k2", "******", "  ", "x")));
        when(d.systemConfigurationService.getConfig(anyString())).thenReturn("x");

        AiProvidersConfigService.ResolvedProvider res = d.svc.resolveProvider(null);
        assertEquals(Map.of("k1", "v1"), res.extraHeaders());
    }

    @Test
    void resolveProviderUsesDecryptedApiKeyAndEncryptedHeadersWhenConfigured() {
        TestDeps d = new TestDeps();

        LlmProviderEntity p = provider(" p1 ", true);
        p.setProviderId("  ");
        p.setType(" local_openai_compat ");
        p.setBaseUrl("  ");
        p.setDefaultChatModel("  ");
        p.setDefaultEmbeddingModel(" emb-x ");
        p.setApiKeyEncrypted(new byte[]{8});
        p.setExtraHeadersEncrypted(new byte[]{9});
        p.setMetadata(new LinkedHashMap<>(Map.of("m", "v")));
        p.setConnectTimeoutMs(3000);
        p.setReadTimeoutMs(6000);

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(p));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(true);
        when(d.llmSecretsCryptoService.decryptStringOrNull(any())).thenReturn("k-real");
        when(d.llmSecretsCryptoService.decryptHeadersOrEmpty(any())).thenReturn(new LinkedHashMap<>(Map.of("h1", " v1 ", "h2", "******")));
        when(d.systemConfigurationService.getConfig("APP_AI_BASE_URL")).thenReturn("https://fallback");
        when(d.systemConfigurationService.getConfig("APP_AI_API_KEY")).thenReturn("k-fallback");
        when(d.systemConfigurationService.getConfig("APP_AI_MODEL")).thenReturn("m-fallback");

        AiProvidersConfigService.ResolvedProvider res = d.svc.resolveProvider(null);
        assertEquals("provider", res.id());
        assertEquals("LOCAL_OPENAI_COMPAT", res.type());
        assertEquals("https://fallback", res.baseUrl());
        assertEquals("k-real", res.apiKey());
        assertEquals("m-fallback", res.defaultChatModel());
        assertEquals("emb-x", res.defaultEmbeddingModel());
        assertEquals(Map.of("h1", "v1"), res.extraHeaders());
        assertEquals(Map.of("m", "v"), res.metadata());
        assertEquals(3000, res.connectTimeoutMs());
        assertEquals(6000, res.readTimeoutMs());
    }

    private static LlmProviderEntity provider(String id, boolean enabled) {
        LlmProviderEntity e = new LlmProviderEntity();
        e.setEnv("default");
        e.setProviderId(id);
        e.setType("OPENAI_COMPAT");
        e.setEnabled(enabled);
        return e;
    }

    private static LlmProviderSettingsEntity settings(String env, String active) {
        LlmProviderSettingsEntity s = new LlmProviderSettingsEntity();
        s.setEnv(env);
        s.setActiveProviderId(active);
        return s;
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
            assertNotNull(svc);
        }
    }
}
