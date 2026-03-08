package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatOptionsDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatProviderOptionDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiProviderDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiProvidersConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatOptionsServiceBranchUnitTest {

    @Test
    void getOptions_shouldFilterProviders_mapModels_andFallbackActiveProvider() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        AiChatOptionsService service = new AiChatOptionsService(aiProvidersConfigService, llmModelRepository);

        AiProviderDTO nullItem = null;
        AiProviderDTO disabled = provider("p-disabled", "Disabled", "m-x", false);
        AiProviderDTO blankId = provider("   ", "BlankId", "m-x", true);
        AiProviderDTO p1 = provider(" p1 ", " Provider One ", " fallback-model ", true);
        AiProviderDTO p2 = provider("p2", "Provider Two", "p2-default", true);

        AiProvidersConfigDTO cfg = new AiProvidersConfigDTO();
        cfg.setActiveProviderId("missing-provider");
        cfg.setProviders(Arrays.asList(nullItem, disabled, blankId, p1, p2));
        when(aiProvidersConfigService.getAdminConfig()).thenReturn(cfg);

        when(llmModelRepository.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc("default", "p1"))
                .thenReturn(Arrays.asList(
                        null,
                        model("TEXT_CHAT", " model-a ", true, true),
                        model("IMAGE_CHAT", " model-b ", true, false),
                        model("CHAT", "model-a", true, false),
                        model("EMBEDDING", "embed-1", true, false),
                        model("TEXT_CHAT", " ", true, false),
                        model("TEXT_CHAT", "off-model", false, true),
                        model("  ", "bad-purpose", true, false)
                ));
        when(llmModelRepository.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc("default", "p2"))
                .thenReturn(List.of());

        AiChatOptionsDTO out = service.getOptions();
        assertNotNull(out);
        assertEquals("p1", out.getActiveProviderId());
        assertNotNull(out.getProviders());
        assertEquals(2, out.getProviders().size());

        AiChatProviderOptionDTO outP1 = out.getProviders().get(0);
        assertEquals("p1", outP1.getId());
        assertEquals("Provider One", outP1.getName());
        assertEquals("fallback-model", outP1.getDefaultChatModel());
        assertNotNull(outP1.getChatModels());
        assertEquals(2, outP1.getChatModels().size());
        assertEquals("model-a", outP1.getChatModels().get(0).getName());
        assertEquals(Boolean.TRUE, outP1.getChatModels().get(0).getIsDefault());
        assertEquals("model-b", outP1.getChatModels().get(1).getName());
        assertEquals(Boolean.FALSE, outP1.getChatModels().get(1).getIsDefault());

        AiChatProviderOptionDTO outP2 = out.getProviders().get(1);
        assertEquals("p2", outP2.getId());
        assertEquals("Provider Two", outP2.getName());
        assertEquals("p2-default", outP2.getDefaultChatModel());
        assertNotNull(outP2.getChatModels());
        assertEquals(1, outP2.getChatModels().size());
        assertEquals("p2-default", outP2.getChatModels().get(0).getName());
        assertEquals(Boolean.TRUE, outP2.getChatModels().get(0).getIsDefault());

        verify(llmModelRepository).findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc("default", "p1");
        verify(llmModelRepository).findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc("default", "p2");
    }

    @Test
    void getOptions_shouldUseFirstProvider_whenActiveProviderMissingOrBlank() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        AiChatOptionsService service = new AiChatOptionsService(aiProvidersConfigService, llmModelRepository);

        AiProvidersConfigDTO cfg = new AiProvidersConfigDTO();
        cfg.setActiveProviderId("   ");
        cfg.setProviders(List.of(provider(" p-first ", "First", null, true)));
        when(aiProvidersConfigService.getAdminConfig()).thenReturn(cfg);
        when(llmModelRepository.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc("default", "p-first"))
                .thenReturn(null);

        AiChatOptionsDTO out = service.getOptions();
        assertNotNull(out);
        assertEquals("p-first", out.getActiveProviderId());
        assertEquals(1, out.getProviders().size());
        assertTrue(out.getProviders().get(0).getChatModels().isEmpty());
    }

    @Test
    void getOptions_shouldReturnEmpty_whenConfigOrProviderListMissing() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        AiChatOptionsService service = new AiChatOptionsService(aiProvidersConfigService, llmModelRepository);

        when(aiProvidersConfigService.getAdminConfig()).thenReturn(null);
        AiChatOptionsDTO out1 = service.getOptions();
        assertNotNull(out1);
        assertNull(out1.getActiveProviderId());
        assertNotNull(out1.getProviders());
        assertTrue(out1.getProviders().isEmpty());

        AiProvidersConfigDTO cfg2 = new AiProvidersConfigDTO();
        cfg2.setActiveProviderId("p-x");
        cfg2.setProviders(null);
        when(aiProvidersConfigService.getAdminConfig()).thenReturn(cfg2);
        AiChatOptionsDTO out2 = service.getOptions();
        assertNotNull(out2);
        assertEquals("p-x", out2.getActiveProviderId());
        assertNotNull(out2.getProviders());
        assertTrue(out2.getProviders().isEmpty());
    }

    @Test
    void getOptions_shouldKeepConfiguredActiveProvider_andSkipFallback_whenDefaultBlank() {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        AiChatOptionsService service = new AiChatOptionsService(aiProvidersConfigService, llmModelRepository);

        AiProviderDTO p1 = provider("p1", "Provider One", "   ", null);
        AiProviderDTO p2 = provider("p2", "Provider Two", null, true);
        AiProvidersConfigDTO cfg = new AiProvidersConfigDTO();
        cfg.setActiveProviderId(" p2 ");
        cfg.setProviders(List.of(p1, p2));
        when(aiProvidersConfigService.getAdminConfig()).thenReturn(cfg);

        when(llmModelRepository.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc("default", "p1"))
                .thenReturn(List.of());
        when(llmModelRepository.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc("default", "p2"))
                .thenReturn(List.of(model("chat", "m2", true, false)));

        AiChatOptionsDTO out = service.getOptions();

        assertNotNull(out);
        assertEquals("p2", out.getActiveProviderId());
        assertNotNull(out.getProviders());
        assertEquals(2, out.getProviders().size());
        assertTrue(out.getProviders().get(0).getChatModels().isEmpty());
        assertEquals(1, out.getProviders().get(1).getChatModels().size());
        assertEquals("m2", out.getProviders().get(1).getChatModels().get(0).getName());
        assertEquals(Boolean.FALSE, out.getProviders().get(1).getChatModels().get(0).getIsDefault());
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadChatModels_shouldReturnEmpty_whenProviderIdBlankOrNull() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        AiChatOptionsService service = new AiChatOptionsService(aiProvidersConfigService, llmModelRepository);

        Method method = AiChatOptionsService.class.getDeclaredMethod("loadChatModels", String.class);
        method.setAccessible(true);
        List<?> out1 = (List<?>) method.invoke(service, new Object[]{null});
        List<?> out2 = (List<?>) method.invoke(service, "   ");

        assertNotNull(out1);
        assertNotNull(out2);
        assertTrue(out1.isEmpty());
        assertTrue(out2.isEmpty());
        verify(llmModelRepository, never()).findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc("default", "   ");
    }

    private static AiProviderDTO provider(String id, String name, String defaultModel, Boolean enabled) {
        AiProviderDTO p = new AiProviderDTO();
        p.setId(id);
        p.setName(name);
        p.setDefaultChatModel(defaultModel);
        p.setEnabled(enabled);
        return p;
    }

    private static LlmModelEntity model(String purpose, String modelName, Boolean enabled, Boolean isDefault) {
        LlmModelEntity e = new LlmModelEntity();
        e.setPurpose(purpose);
        e.setModelName(modelName);
        e.setEnabled(enabled);
        e.setIsDefault(isDefault);
        return e;
    }
}
