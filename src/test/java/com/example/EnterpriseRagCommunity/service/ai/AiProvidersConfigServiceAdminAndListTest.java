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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AiProvidersConfigServiceAdminAndListTest {

    @Test
    void getAdminConfigLoadsDbAndMasksSecretsAndHeaders() {
        TestDeps d = new TestDeps();

        LlmProviderEntity row = new LlmProviderEntity();
        row.setEnv("default");
        row.setProviderId("p1");
        row.setName("Provider1");
        row.setType("OPENAI_COMPAT");
        row.setBaseUrl("https://api.example.com");
        row.setDefaultChatModel("gpt-x");
        row.setDefaultEmbeddingModel("embed-x");
        row.setMetadata(new LinkedHashMap<>(Map.of(
                "defaultRerankModel", " rerank-x ",
                "rerankEndpointPath", " /rerank ",
                "supportsVision", true
        )));
        row.setExtraHeadersEncrypted(new byte[]{1});
        row.setConnectTimeoutMs(3000);
        row.setReadTimeoutMs(6000);
        row.setEnabled(true);

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(row));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.of(settings("default", "p1")));
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(true);
        when(d.llmSecretsCryptoService.decryptHeadersOrEmpty(any()))
                .thenReturn(new LinkedHashMap<>(Map.of("h1", "v1", "h2", "******", "  ", "x")));

        AiProvidersConfigDTO out = d.svc.getAdminConfig();
        assertEquals("p1", out.getActiveProviderId());
        assertEquals(1, out.getProviders().size());
        AiProviderDTO p = out.getProviders().get(0);
        assertEquals("******", p.getApiKey());
        assertEquals(Map.of("h1", "******"), p.getExtraHeaders());
        assertEquals("rerank-x", p.getDefaultRerankModel());
        assertEquals("/rerank", p.getRerankEndpointPath());
        assertEquals(true, p.getSupportsVision());
    }

    @Test
    void listEnabledProviderIdsPrefersDbRowsAndDeduplicates() {
        TestDeps d = new TestDeps();

        LlmProviderEntity blank = new LlmProviderEntity();
        blank.setProviderId("   ");
        LlmProviderEntity p1a = new LlmProviderEntity();
        p1a.setProviderId("p1");
        LlmProviderEntity p1b = new LlmProviderEntity();
        p1b.setProviderId("p1");
        LlmProviderEntity p2 = new LlmProviderEntity();
        p2.setProviderId(" p2 ");

        when(d.llmProviderRepository.findByEnvAndEnabledTrueOrderByPriorityAscIdAsc("default"))
                .thenReturn(Arrays.asList(null, blank, p1a, p1b, p2));

        List<String> out = d.svc.listEnabledProviderIds();
        assertEquals(List.of("p1", "p2"), out);
        verify(d.appSettingsService, never()).getString(anyString());
    }

    @Test
    void listEnabledProviderIdsFallsBackToLegacyWhenDbEmpty() {
        TestDeps d = new TestDeps();

        when(d.llmProviderRepository.findByEnvAndEnabledTrueOrderByPriorityAscIdAsc("default")).thenReturn(List.of());
        when(d.appSettingsService.getString(AiProvidersConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of(
                "{\"activeProviderId\":\"p1\",\"providers\":[{\"id\":\" p1 \",\"enabled\":true},{\"id\":\"p2\",\"enabled\":false},{\"id\":\"  \",\"enabled\":true},{\"id\":\"p1\",\"enabled\":true}]}"
        ));

        List<String> out = d.svc.listEnabledProviderIds();
        assertEquals(List.of("p1"), out);
    }

    @Test
    void resolveProviderThrowsWhenDbEmptyAndLegacyAllDisabled() {
        TestDeps d = new TestDeps();

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of());
        when(d.appSettingsService.getString(AiProvidersConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of(
                "{\"activeProviderId\":\"none\",\"providers\":[{\"id\":\"p1\",\"enabled\":false},{\"id\":\"p2\",\"enabled\":false}]}"
        ));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> d.svc.resolveProvider(null));
        assertTrue(ex.getMessage().contains("未配置任何有效"));
    }

    @Test
    void getAdminConfigFallsBackToFirstProviderWhenActiveMissingAndUnconfiguredSecrets() {
        TestDeps d = new TestDeps();

        LlmProviderEntity row = new LlmProviderEntity();
        row.setEnv("default");
        row.setProviderId(" p1 ");
        row.setType("OPENAI_COMPAT");
        row.setMetadata(new LinkedHashMap<>(Map.of(
                "defaultRerankModel", "  ",
                "rerankEndpointPath", 123,
                "supportsVision", "yes"
        )));
        row.setExtraHeadersEncrypted(new byte[]{1});
        row.setEnabled(true);

        when(d.llmProviderRepository.findByEnvOrderByPriorityAscIdAsc("default")).thenReturn(List.of(row));
        when(d.llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(d.llmSecretsCryptoService.isConfigured()).thenReturn(false);

        AiProvidersConfigDTO out = d.svc.getAdminConfig();
        assertEquals("p1", out.getActiveProviderId());
        assertEquals(1, out.getProviders().size());
        AiProviderDTO p = out.getProviders().get(0);
        assertEquals(Map.of(), p.getExtraHeaders());
        assertEquals(null, p.getDefaultRerankModel());
        assertEquals(null, p.getRerankEndpointPath());
        assertEquals(null, p.getSupportsVision());
    }

    private static LlmProviderSettingsEntity settings(String env, String activeId) {
        LlmProviderSettingsEntity s = new LlmProviderSettingsEntity();
        s.setEnv(env);
        s.setActiveProviderId(activeId);
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
        }
    }
}
