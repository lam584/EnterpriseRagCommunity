package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PortalChatConfigServiceTest {

    @Test
    void getConfigOrDefaultFallsBackOnInvalidJson() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(appSettingsService.getString(PortalChatConfigService.KEY_PORTAL_CHAT_CONFIG_V1)).thenReturn(Optional.of("{bad"));

        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);
        PortalChatConfigDTO cfg = svc.getConfigOrDefault();
        assertNotNull(cfg);
        assertNotNull(cfg.getAssistantChat());
        assertNotNull(cfg.getPostComposeAssistant());
        assertEquals(20, cfg.getAssistantChat().getHistoryLimit());
        assertEquals(6, cfg.getAssistantChat().getRagTopK());
        assertTrue(cfg.getAssistantChat().getSystemPrompt() != null && !cfg.getAssistantChat().getSystemPrompt().isBlank());
        assertTrue(cfg.getPostComposeAssistant().getComposeSystemPrompt() != null && !cfg.getPostComposeAssistant().getComposeSystemPrompt().isBlank());
    }

    @Test
    void upsertRejectsBlankPrompts() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);

        PortalChatConfigDTO payload = new PortalChatConfigDTO();
        PortalChatConfigDTO.AssistantChatConfigDTO a = new PortalChatConfigDTO.AssistantChatConfigDTO();
        a.setSystemPrompt(" ");
        a.setDeepThinkSystemPrompt("ok");
        payload.setAssistantChat(a);
        PortalChatConfigDTO.PostComposeAssistantConfigDTO p = new PortalChatConfigDTO.PostComposeAssistantConfigDTO();
        p.setSystemPrompt("ok");
        p.setDeepThinkSystemPrompt("ok");
        p.setComposeSystemPrompt("ok");
        payload.setPostComposeAssistant(p);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.upsertAdminConfig(payload));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("assistantChat.systemPrompt"));
    }

    @Test
    void upsertWritesNormalizedJsonToAppSettings() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);

        PortalChatConfigDTO payload = svc.getConfigOrDefault();
        payload.getAssistantChat().setHistoryLimit(99);
        payload.getAssistantChat().setRagTopK(7);
        payload.getAssistantChat().setTemperature(0.3);
        payload.getAssistantChat().setTopP(0.8);
        payload.getAssistantChat().setProviderId("p1");
        payload.getAssistantChat().setModel("m1");

        svc.upsertAdminConfig(payload);

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCap = ArgumentCaptor.forClass(String.class);
        verify(appSettingsService).upsertString(keyCap.capture(), valueCap.capture());
        assertEquals(PortalChatConfigService.KEY_PORTAL_CHAT_CONFIG_V1, keyCap.getValue());

        PortalChatConfigDTO saved = objectMapper.readValue(valueCap.getValue(), PortalChatConfigDTO.class);
        assertNotNull(saved);
        assertEquals(99, saved.getAssistantChat().getHistoryLimit());
        assertEquals(7, saved.getAssistantChat().getRagTopK());
        assertEquals("p1", saved.getAssistantChat().getProviderId());
        assertEquals("m1", saved.getAssistantChat().getModel());
    }
}
