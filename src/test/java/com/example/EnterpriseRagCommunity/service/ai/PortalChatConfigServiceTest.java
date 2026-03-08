package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        assertTrue(cfg.getAssistantChat().getSystemPromptCode() != null && !cfg.getAssistantChat().getSystemPromptCode().isBlank());
        assertTrue(cfg.getPostComposeAssistant().getComposeSystemPromptCode() != null && !cfg.getPostComposeAssistant().getComposeSystemPromptCode().isBlank());
    }

    @Test
    void getAdminConfigDelegatesAndSupportsMissingOrBlankSettings() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);

        when(appSettingsService.getString(PortalChatConfigService.KEY_PORTAL_CHAT_CONFIG_V1))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of("   "));

        PortalChatConfigDTO missingCfg = svc.getAdminConfig();
        PortalChatConfigDTO blankCfg = svc.getAdminConfig();

        assertNotNull(missingCfg);
        assertEquals(20, missingCfg.getAssistantChat().getHistoryLimit());
        assertEquals("PORTAL_POST_COMPOSE_PROTOCOL", missingCfg.getPostComposeAssistant().getComposeSystemPromptCode());
        assertNotNull(blankCfg);
        assertEquals(20, blankCfg.getPostComposeAssistant().getChatHistoryLimit());
        verify(objectMapper, never()).readValue(any(String.class), any(Class.class));
    }

    @Test
    void getConfigOrDefaultHandlesParsedNullAndTrimsOptionalStrings() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);

        PortalChatConfigDTO parsed = createValidPayload();
        parsed.getAssistantChat().setProviderId("  provider-1  ");
        parsed.getAssistantChat().setModel("   ");
        parsed.getAssistantChat().setDefaultUseRag(false);
        parsed.getAssistantChat().setDefaultStream(false);
        parsed.getPostComposeAssistant().setProviderId("   ");
        parsed.getPostComposeAssistant().setModel(" model-x ");
        parsed.getPostComposeAssistant().setDefaultDeepThink(true);

        when(appSettingsService.getString(PortalChatConfigService.KEY_PORTAL_CHAT_CONFIG_V1))
                .thenReturn(Optional.of("{json1}"))
                .thenReturn(Optional.of("{json2}"));
        when(objectMapper.readValue("{json1}", PortalChatConfigDTO.class)).thenReturn(null);
        when(objectMapper.readValue("{json2}", PortalChatConfigDTO.class)).thenReturn(parsed);

        PortalChatConfigDTO nullParsed = svc.getConfigOrDefault();
        PortalChatConfigDTO trimmed = svc.getConfigOrDefault();

        assertEquals("PORTAL_CHAT_ASSISTANT", nullParsed.getAssistantChat().getSystemPromptCode());
        assertEquals("provider-1", trimmed.getAssistantChat().getProviderId());
        assertNull(trimmed.getAssistantChat().getModel());
        assertFalse(trimmed.getAssistantChat().getDefaultUseRag());
        assertFalse(trimmed.getAssistantChat().getDefaultStream());
        assertNull(trimmed.getPostComposeAssistant().getProviderId());
        assertEquals("model-x", trimmed.getPostComposeAssistant().getModel());
        assertTrue(trimmed.getPostComposeAssistant().getDefaultDeepThink());
    }

    @Test
    void upsertRejectsBlankPrompts() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);

        PortalChatConfigDTO payload = new PortalChatConfigDTO();
        PortalChatConfigDTO.AssistantChatConfigDTO a = new PortalChatConfigDTO.AssistantChatConfigDTO();
        a.setSystemPromptCode(" ");
        a.setDeepThinkSystemPromptCode("ok");
        payload.setAssistantChat(a);
        PortalChatConfigDTO.PostComposeAssistantConfigDTO p = new PortalChatConfigDTO.PostComposeAssistantConfigDTO();
        p.setSystemPromptCode("ok");
        p.setDeepThinkSystemPromptCode("ok");
        p.setComposeSystemPromptCode("ok");
        payload.setPostComposeAssistant(p);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.upsertAdminConfig(payload));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("assistantChat.systemPromptCode"));
    }

    @Test
    void getConfigOrDefaultNormalizesInvalidValuesInNonStrictMode() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);

        PortalChatConfigDTO payload = createValidPayload();
        payload.getAssistantChat().setTemperature(-1.0);
        payload.getAssistantChat().setTopP(3.0);
        payload.getAssistantChat().setHistoryLimit(0);
        payload.getAssistantChat().setRagTopK(999);
        payload.getAssistantChat().setSystemPromptCode(" ");
        payload.getAssistantChat().setDeepThinkSystemPromptCode(" ");
        payload.getPostComposeAssistant().setTemperature(-0.2);
        payload.getPostComposeAssistant().setTopP(8.0);
        payload.getPostComposeAssistant().setChatHistoryLimit(999);
        payload.getPostComposeAssistant().setSystemPromptCode(" ");
        payload.getPostComposeAssistant().setDeepThinkSystemPromptCode(" ");
        payload.getPostComposeAssistant().setComposeSystemPromptCode(" ");

        when(appSettingsService.getString(PortalChatConfigService.KEY_PORTAL_CHAT_CONFIG_V1))
                .thenReturn(Optional.of(objectMapper.writeValueAsString(payload)));

        PortalChatConfigDTO cfg = svc.getConfigOrDefault();
        assertEquals(0.0, cfg.getAssistantChat().getTemperature());
        assertEquals(1.0, cfg.getAssistantChat().getTopP());
        assertEquals(1, cfg.getAssistantChat().getHistoryLimit());
        assertEquals(50, cfg.getAssistantChat().getRagTopK());
        assertEquals("PORTAL_CHAT_ASSISTANT", cfg.getAssistantChat().getSystemPromptCode());
        assertEquals("PORTAL_CHAT_ASSISTANT_DEEP_THINK", cfg.getAssistantChat().getDeepThinkSystemPromptCode());
        assertEquals(0.0, cfg.getPostComposeAssistant().getTemperature());
        assertEquals(1.0, cfg.getPostComposeAssistant().getTopP());
        assertEquals(200, cfg.getPostComposeAssistant().getChatHistoryLimit());
        assertEquals("PORTAL_POST_COMPOSE", cfg.getPostComposeAssistant().getSystemPromptCode());
        assertEquals("PORTAL_POST_COMPOSE_DEEP_THINK", cfg.getPostComposeAssistant().getDeepThinkSystemPromptCode());
        assertEquals("PORTAL_POST_COMPOSE_PROTOCOL", cfg.getPostComposeAssistant().getComposeSystemPromptCode());
    }

    @Test
    void upsertRejectsOutOfRangeNumberInStrictMode() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);

        PortalChatConfigDTO payload = createValidPayload();
        payload.getAssistantChat().setTemperature(2.5);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.upsertAdminConfig(payload));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("assistantChat.temperature"));
    }

    @Test
    void upsertRejectsNonFiniteNumbersInStrictMode() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);

        PortalChatConfigDTO payload1 = createValidPayload();
        payload1.getAssistantChat().setTemperature(Double.NaN);
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> svc.upsertAdminConfig(payload1));
        assertTrue(ex1.getMessage().contains("assistantChat.temperature"));

        PortalChatConfigDTO payload2 = createValidPayload();
        payload2.getPostComposeAssistant().setTopP(Double.POSITIVE_INFINITY);
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> svc.upsertAdminConfig(payload2));
        assertTrue(ex2.getMessage().contains("postComposeAssistant.topP"));
    }

    @Test
    void getConfigOrDefaultNonStrictDropsNonFiniteNumbersAndTruncatesOverlongPrompt() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);

        String tooLong = "x".repeat(20001);
        PortalChatConfigDTO payload = createValidPayload();
        payload.getAssistantChat().setTemperature(Double.NEGATIVE_INFINITY);
        payload.getAssistantChat().setTopP(Double.NaN);
        payload.getAssistantChat().setDeepThinkSystemPromptCode(tooLong);
        payload.getPostComposeAssistant().setTemperature(Double.POSITIVE_INFINITY);
        payload.getPostComposeAssistant().setTopP(Double.NaN);
        payload.getPostComposeAssistant().setDeepThinkSystemPromptCode(tooLong);

        when(appSettingsService.getString(PortalChatConfigService.KEY_PORTAL_CHAT_CONFIG_V1))
                .thenReturn(Optional.of(objectMapper.writeValueAsString(payload)));

        PortalChatConfigDTO cfg = svc.getConfigOrDefault();
        assertNull(cfg.getAssistantChat().getTemperature());
        assertNull(cfg.getAssistantChat().getTopP());
        assertEquals(20000, cfg.getAssistantChat().getDeepThinkSystemPromptCode().length());
        assertNull(cfg.getPostComposeAssistant().getTemperature());
        assertNull(cfg.getPostComposeAssistant().getTopP());
        assertEquals(20000, cfg.getPostComposeAssistant().getDeepThinkSystemPromptCode().length());
    }

    @Test
    void upsertRejectsOverlongPromptInStrictMode() {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);

        PortalChatConfigDTO payload = createValidPayload();
        payload.getPostComposeAssistant().setComposeSystemPromptCode("x".repeat(20001));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.upsertAdminConfig(payload));
        assertTrue(ex.getMessage().contains("postComposeAssistant.composeSystemPromptCode"));
    }

    @Test
    void upsertThrowsWhenSerializationFails() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });
        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);

        PortalChatConfigDTO payload = createValidPayload();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.upsertAdminConfig(payload));
        assertEquals("配置序列化失败", ex.getMessage());
    }

    @Test
    void upsertWritesNormalizedJsonToAppSettings() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PortalChatConfigService svc = new PortalChatConfigService(appSettingsService, objectMapper);

        PortalChatConfigDTO payload = createValidPayload();
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

    private static PortalChatConfigDTO createValidPayload() {
        PortalChatConfigDTO payload = new PortalChatConfigDTO();
        PortalChatConfigDTO.AssistantChatConfigDTO a = new PortalChatConfigDTO.AssistantChatConfigDTO();
        a.setSystemPromptCode("assistant");
        a.setDeepThinkSystemPromptCode("assistant.deep");
        payload.setAssistantChat(a);
        PortalChatConfigDTO.PostComposeAssistantConfigDTO p = new PortalChatConfigDTO.PostComposeAssistantConfigDTO();
        p.setSystemPromptCode("post");
        p.setDeepThinkSystemPromptCode("post.deep");
        p.setComposeSystemPromptCode("post.compose");
        payload.setPostComposeAssistant(p);
        return payload;
    }
}
