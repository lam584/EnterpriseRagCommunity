package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AssistantPreferencesDTO;
import com.example.EnterpriseRagCommunity.dto.ai.UpdateAssistantPreferencesRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.PortalChatConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAssistantPreferencesControllerTest {

        private static ObjectProvider<PortalChatConfigService> emptyPortalChatConfigProvider() {
                return new DefaultListableBeanFactory().getBeanProvider(PortalChatConfigService.class);
        }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getPreferences_authNull_returns401() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AiAssistantPreferencesController c = new AiAssistantPreferencesController(
                usersRepository,
                auditLogWriter,
                auditDiffBuilder,
                emptyPortalChatConfigProvider()
        );

        SecurityContextHolder.getContext().setAuthentication(null);

        ResponseEntity<?> resp = c.getPreferences();
        Assertions.assertEquals(401, resp.getStatusCode().value());
        Assertions.assertEquals("未登录或会话已过期", ((Map<?, ?>) resp.getBody()).get("message"));
    }

    @Test
    void updatePreferences_notAuthenticated_returns401() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AiAssistantPreferencesController c = new AiAssistantPreferencesController(
                usersRepository,
                auditLogWriter,
                auditDiffBuilder,
                emptyPortalChatConfigProvider()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A")
        );

        ResponseEntity<?> resp = c.updatePreferences(new UpdateAssistantPreferencesRequest());
        Assertions.assertEquals(401, resp.getStatusCode().value());
        Assertions.assertEquals("未登录或会话已过期", ((Map<?, ?>) resp.getBody()).get("message"));
    }

    @Test
    void getPreferences_userNotFound_throwsRuntimeException() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AiAssistantPreferencesController c = new AiAssistantPreferencesController(
                usersRepository,
                auditLogWriter,
                auditDiffBuilder,
                emptyPortalChatConfigProvider()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
        when(usersRepository.findByEmailAndIsDeletedFalse("alice@example.com")).thenReturn(Optional.empty());

        Assertions.assertThrows(RuntimeException.class, c::getPreferences);
    }

    @Test
    void getPreferences_metadataNull_returnsDefaults() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AiAssistantPreferencesController c = new AiAssistantPreferencesController(
                usersRepository,
                auditLogWriter,
                auditDiffBuilder,
                emptyPortalChatConfigProvider()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
        UsersEntity u = new UsersEntity();
        u.setId(10L);
        u.setEmail("alice@example.com");
        u.setMetadata(null);
        when(usersRepository.findByEmailAndIsDeletedFalse("alice@example.com")).thenReturn(Optional.of(u));

        ResponseEntity<?> resp = c.getPreferences();
        AssistantPreferencesDTO dto = (AssistantPreferencesDTO) resp.getBody();
        Assertions.assertNull(dto.getDefaultProviderId());
        Assertions.assertNull(dto.getDefaultModel());
        Assertions.assertFalse(dto.isDefaultDeepThink());
        Assertions.assertFalse(dto.isAutoLoadLastSession());
        Assertions.assertTrue(dto.isDefaultUseRag());
        Assertions.assertEquals(6, dto.getRagTopK());
        Assertions.assertTrue(dto.isStream());
        Assertions.assertNull(dto.getTemperature());
        Assertions.assertNull(dto.getTopP());
        Assertions.assertNull(dto.getDefaultSystemPrompt());
    }

    @Test
    void getPreferences_parsesAndClampsMetadataValues() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AiAssistantPreferencesController c = new AiAssistantPreferencesController(
                usersRepository,
                auditLogWriter,
                auditDiffBuilder,
                emptyPortalChatConfigProvider()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("defaultProviderId", "  p1 ");
        assistant.put("defaultModel", 123);
        assistant.put("defaultDeepThink", true);
        assistant.put("autoLoadLastSession", "true");
        assistant.put("defaultUseRag", false);
        assistant.put("ragTopK", 100);
        assistant.put("stream", false);
        assistant.put("temperature", -1);
        assistant.put("topP", 2);
        assistant.put("defaultSystemPrompt", "  ");

        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("assistant", assistant);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("preferences", prefs);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        u.setEmail("alice@example.com");
        u.setMetadata(metadata);
        when(usersRepository.findByEmailAndIsDeletedFalse("alice@example.com")).thenReturn(Optional.of(u));

        ResponseEntity<?> resp = c.getPreferences();
        AssistantPreferencesDTO dto = (AssistantPreferencesDTO) resp.getBody();
        Assertions.assertEquals("p1", dto.getDefaultProviderId());
        Assertions.assertEquals("123", dto.getDefaultModel());
        Assertions.assertTrue(dto.isDefaultDeepThink());
        Assertions.assertFalse(dto.isAutoLoadLastSession());
        Assertions.assertFalse(dto.isDefaultUseRag());
        Assertions.assertEquals(50, dto.getRagTopK());
        Assertions.assertFalse(dto.isStream());
        Assertions.assertEquals(0.0, dto.getTemperature());
        Assertions.assertEquals(1.0, dto.getTopP());
        Assertions.assertNull(dto.getDefaultSystemPrompt());
    }

    @Test
    void updatePreferences_metadataNull_appliesNormalizationAndWritesAudit() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        AiAssistantPreferencesController c = new AiAssistantPreferencesController(
                usersRepository,
                auditLogWriter,
                auditDiffBuilder,
                emptyPortalChatConfigProvider()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        u.setEmail("alice@example.com");
        u.setMetadata(null);
        when(usersRepository.findByEmailAndIsDeletedFalse("alice@example.com")).thenReturn(Optional.of(u));
        when(usersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateAssistantPreferencesRequest req = new UpdateAssistantPreferencesRequest();
        req.setDefaultProviderId("   ");
        req.setDefaultModel("m1");
        req.setDefaultDeepThink(null);
        req.setAutoLoadLastSession(true);
        req.setDefaultUseRag(false);
        req.setRagTopK(0);
        req.setStream(null);
        req.setTemperature(3.0);
        req.setTopP(-0.1);
        req.setDefaultSystemPrompt(" hi ");

        ResponseEntity<?> resp = c.updatePreferences(req);
        AssistantPreferencesDTO dto = (AssistantPreferencesDTO) resp.getBody();

        Assertions.assertNull(dto.getDefaultProviderId());
        Assertions.assertEquals("m1", dto.getDefaultModel());
        Assertions.assertFalse(dto.isDefaultDeepThink());
        Assertions.assertTrue(dto.isAutoLoadLastSession());
        Assertions.assertFalse(dto.isDefaultUseRag());
        Assertions.assertEquals(1, dto.getRagTopK());
        Assertions.assertFalse(dto.isStream());
        Assertions.assertEquals(2.0, dto.getTemperature());
        Assertions.assertEquals(0.0, dto.getTopP());
        Assertions.assertEquals("hi", dto.getDefaultSystemPrompt());
        verify(auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updatePreferences_nonMapPreferences_replacesWithMap() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        AiAssistantPreferencesController c = new AiAssistantPreferencesController(
                usersRepository,
                auditLogWriter,
                auditDiffBuilder,
                emptyPortalChatConfigProvider()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        u.setEmail("alice@example.com");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("preferences", "oops");
        u.setMetadata(metadata);

        when(usersRepository.findByEmailAndIsDeletedFalse("alice@example.com")).thenReturn(Optional.of(u));
        when(usersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateAssistantPreferencesRequest req = new UpdateAssistantPreferencesRequest();
        req.setDefaultModel("m1");

        c.updatePreferences(req);

        Object p = u.getMetadata().get("preferences");
        Assertions.assertTrue(p instanceof Map);
        Object a = ((Map<?, ?>) p).get("assistant");
        Assertions.assertTrue(a instanceof Map);
        Assertions.assertEquals("m1", ((Map<?, ?>) a).get("defaultModel"));
    }

    @Test
    void updatePreferences_defaultSystemPromptLen_branchCovered() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        AiAssistantPreferencesController c = new AiAssistantPreferencesController(
                usersRepository,
                auditLogWriter,
                auditDiffBuilder,
                emptyPortalChatConfigProvider()
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("defaultSystemPrompt", null);
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("assistant", assistant);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("preferences", prefs);

        UsersEntity u = new UsersEntity();
        u.setId(10L);
        u.setEmail("alice@example.com");
        u.setMetadata(metadata);

        when(usersRepository.findByEmailAndIsDeletedFalse("alice@example.com")).thenReturn(Optional.of(u));
        when(usersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateAssistantPreferencesRequest req = new UpdateAssistantPreferencesRequest();
        req.setDefaultSystemPrompt("abc");
        c.updatePreferences(req);
        verify(auditDiffBuilder).build(any(), any());
    }
}

