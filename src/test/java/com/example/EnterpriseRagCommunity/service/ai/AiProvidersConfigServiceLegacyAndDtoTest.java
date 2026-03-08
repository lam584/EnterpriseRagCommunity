package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiProviderDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiProvidersConfigDTO;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderSettingsRepository;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AiProvidersConfigServiceLegacyAndDtoTest {

    @Test
    void normalizeDropsInvalidProvidersAndDefaultsActiveEnabledTimeoutsAndHeaders() throws Exception {
        Method m = AiProvidersConfigService.class.getDeclaredMethod("normalize", AiProvidersConfigDTO.class);
        m.setAccessible(true);

        AiProvidersConfigDTO in = new AiProvidersConfigDTO();
        in.setActiveProviderId("   ");

        AiProviderDTO badNullId = new AiProviderDTO();
        badNullId.setId("  ");

        AiProviderDTO p1 = new AiProviderDTO();
        p1.setId(" p1 ");
        p1.setEnabled(null);
        p1.setConnectTimeoutMs(0);
        p1.setReadTimeoutMs(-1);
        p1.setApiKey("******");
        p1.setExtraHeaders(Map.of("  ", "x", "k1", "  ", "k2", "******", "k3", " v3 "));

        in.setProviders(Arrays.asList(null, badNullId, p1));

        AiProvidersConfigDTO out = (AiProvidersConfigDTO) m.invoke(newService(), in);

        assertEquals(1, out.getProviders().size());
        AiProviderDTO x = out.getProviders().get(0);
        assertEquals("p1", x.getId());
        assertEquals(true, x.getEnabled());
        assertNull(x.getConnectTimeoutMs());
        assertNull(x.getReadTimeoutMs());
        assertNull(x.getApiKey());
        assertEquals(Map.of("k3", "v3"), x.getExtraHeaders());
        assertEquals("p1", out.getActiveProviderId());
    }

    @Test
    void loadLegacyOrDefaultFallsBackToDefaultWhenJsonBlankOrInvalid() throws Exception {
        AiProvidersConfigService svc = newService();
        AppSettingsService appSettingsService = getField(svc, "appSettingsService", AppSettingsService.class);

        when(appSettingsService.getString(AiProvidersConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of("  "));
        AiProvidersConfigDTO blank = invokeLoadLegacyOrDefault(svc);
        assertEquals("default", blank.getActiveProviderId());
        assertEquals(1, blank.getProviders().size());
        assertEquals("default", blank.getProviders().get(0).getId());

        when(appSettingsService.getString(AiProvidersConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of("{"));
        AiProvidersConfigDTO invalid = invokeLoadLegacyOrDefault(svc);
        assertEquals("default", invalid.getActiveProviderId());
        assertEquals(1, invalid.getProviders().size());
        assertEquals("default", invalid.getProviders().get(0).getId());
    }

    @Test
    void loadLegacyOrDefaultParsesValidJsonAndNormalizesTimeoutHeaders() throws Exception {
        AiProvidersConfigService svc = newService();
        AppSettingsService appSettingsService = getField(svc, "appSettingsService", AppSettingsService.class);

        when(appSettingsService.getString(AiProvidersConfigService.KEY_CONFIG_JSON)).thenReturn(Optional.of(
                "{\"activeProviderId\":\" \",\"providers\":[{\"id\":\" p1 \",\"enabled\":null,\"connectTimeoutMs\":0,\"readTimeoutMs\":-2,\"extraHeaders\":{\"a\":\" 1 \",\"b\":\"******\",\" \":\"x\"}}]}"
        ));
        AiProvidersConfigDTO out = invokeLoadLegacyOrDefault(svc);
        assertEquals("p1", out.getActiveProviderId());
        assertEquals(1, out.getProviders().size());
        AiProviderDTO p = out.getProviders().get(0);
        assertEquals("p1", p.getId());
        assertEquals(true, p.getEnabled());
        assertNull(p.getConnectTimeoutMs());
        assertNull(p.getReadTimeoutMs());
        assertEquals(Map.of("a", "1"), p.getExtraHeaders());
    }

    @Test
    void resolveFromDtoPicksDesiredThenActiveThenFirstEnabledAndAppliesDefaults() {
        AiProvidersConfigService svc = newService();

        SystemConfigurationService systemConfigurationService = getField(svc, "systemConfigurationService", SystemConfigurationService.class);
        when(systemConfigurationService.getConfig("APP_AI_BASE_URL")).thenReturn("https://base");
        when(systemConfigurationService.getConfig("APP_AI_API_KEY")).thenReturn("k0");
        when(systemConfigurationService.getConfig("APP_AI_MODEL")).thenReturn("m0");

        AiProviderDTO p1 = new AiProviderDTO();
        p1.setId("p1");
        p1.setEnabled(false);
        p1.setApiKey("******");

        AiProviderDTO p2 = new AiProviderDTO();
        p2.setId("p2");
        p2.setEnabled(true);

        AiProvidersConfigDTO cfg = new AiProvidersConfigDTO();
        cfg.setActiveProviderId("p1");
        cfg.setProviders(List.of(p1, p2));

        AiProvidersConfigService.ResolvedProvider desired = invokeResolveFromDto(svc, cfg, "p2");
        assertEquals("p2", desired.id());
        assertEquals("https://base", desired.baseUrl());
        assertEquals("k0", desired.apiKey());
        assertEquals("m0", desired.defaultChatModel());

        AiProvidersConfigService.ResolvedProvider active = invokeResolveFromDto(svc, cfg, null);
        assertEquals("p1", active.id());

        cfg.setActiveProviderId("missing");
        AiProvidersConfigService.ResolvedProvider firstEnabled = invokeResolveFromDto(svc, cfg, null);
        assertEquals("p2", firstEnabled.id());

        cfg.setProviders(List.of(new AiProviderDTO()));
        AiProvidersConfigService.ResolvedProvider fallback = invokeResolveFromDto(svc, cfg, null);
        assertEquals("provider", fallback.id());
    }

    @Test
    void resolveFromDtoThrowsWhenNoValidProviderInLegacyConfig() {
        AiProvidersConfigService svc = newService();

        AiProviderDTO p1 = new AiProviderDTO();
        p1.setId("p1");
        p1.setEnabled(false);
        AiProviderDTO p2 = new AiProviderDTO();
        p2.setId("p2");
        p2.setEnabled(false);

        AiProvidersConfigDTO cfg = new AiProvidersConfigDTO();
        cfg.setProviders(List.of(p1, p2));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> invokeResolveFromDto(svc, cfg, null));
        assertTrue(ex.getMessage().contains("未配置任何有效"));
    }

    private static AiProvidersConfigDTO invokeLoadLegacyOrDefault(AiProvidersConfigService svc) throws Exception {
        Method m = AiProvidersConfigService.class.getDeclaredMethod("loadLegacyOrDefault");
        m.setAccessible(true);
        return (AiProvidersConfigDTO) m.invoke(svc);
    }

    private static AiProvidersConfigService.ResolvedProvider invokeResolveFromDto(AiProvidersConfigService svc, AiProvidersConfigDTO cfg, String providerId) {
        try {
            Method m = AiProvidersConfigService.class.getDeclaredMethod("resolveFromDto", AiProvidersConfigDTO.class, String.class);
            m.setAccessible(true);
            return (AiProvidersConfigService.ResolvedProvider) m.invoke(svc, cfg, providerId);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new RuntimeException(e);
        }
    }

    private static AiProvidersConfigService newService() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        LlmProviderRepository llmProviderRepository = mock(LlmProviderRepository.class);
        LlmProviderSettingsRepository llmProviderSettingsRepository = mock(LlmProviderSettingsRepository.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        LlmSecretsCryptoService llmSecretsCryptoService = mock(LlmSecretsCryptoService.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);

        when(appSettingsService.getString(anyString())).thenReturn(Optional.empty());
        when(systemConfigurationService.getConfig(anyString())).thenReturn(null);

        return new AiProvidersConfigService(
                appSettingsService,
                objectMapper,
                llmProviderRepository,
                llmProviderSettingsRepository,
                llmModelRepository,
                llmSecretsCryptoService,
                systemConfigurationService,
                llmRoutingService
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object obj, String name, Class<T> type) {
        try {
            var f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(obj);
            assertNotNull(v);
            return (T) v;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
