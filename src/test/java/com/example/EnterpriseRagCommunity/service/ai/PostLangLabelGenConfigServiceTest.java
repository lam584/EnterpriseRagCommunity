package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostLangLabelGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostLangLabelGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostLangLabelGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostLangLabelGenConfigRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PostLangLabelGenConfigServiceTest {

    private PostLangLabelGenConfigRepository configRepository;
    private PromptsRepository promptsRepository;
    private PostLangLabelGenConfigService service;

    @BeforeEach
    void setUp() {
        configRepository = mock(PostLangLabelGenConfigRepository.class);
        promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.empty());
        when(configRepository.save(any(PostLangLabelGenConfigEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new PostLangLabelGenConfigService(configRepository, promptsRepository);
    }

    @Test
    void getAdminConfigReturnsDefaultWhenMissing() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.empty());

        PostLangLabelGenConfigDTO dto = service.getAdminConfig();

        assertNotNull(dto);
        assertTrue(Boolean.TRUE.equals(dto.getEnabled()));
        assertEquals("LANG_DETECT", dto.getPromptCode());
        assertEquals(8000, dto.getMaxContentChars());
        assertEquals(0, dto.getVersion());
        assertNull(dto.getModel());
        assertNull(dto.getProviderId());
        assertNull(dto.getEnableThinking());
    }

    @Test
    void getAdminConfigMapsPromptFieldsWhenPromptExists() {
        PostLangLabelGenConfigEntity entity = baseEntity();
        entity.setEnabled(Boolean.FALSE);
        entity.setPromptCode("P1");
        entity.setMaxContentChars(1234);
        entity.setVersion(2);
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(entity));

        PromptsEntity prompt = promptEntity();
        when(promptsRepository.findByPromptCode("P1")).thenReturn(Optional.of(prompt));

        PostLangLabelGenConfigDTO dto = service.getAdminConfig();

        assertNotNull(dto);
        assertFalse(Boolean.TRUE.equals(dto.getEnabled()));
        assertEquals("P1", dto.getPromptCode());
        assertEquals(1234, dto.getMaxContentChars());
        assertEquals(2, dto.getVersion());
        assertEquals(prompt.getModelName(), dto.getModel());
        assertEquals(prompt.getProviderId(), dto.getProviderId());
        assertEquals(prompt.getTemperature(), dto.getTemperature());
        assertEquals(prompt.getTopP(), dto.getTopP());
        assertEquals(prompt.getEnableDeepThinking(), dto.getEnableThinking());
        assertNull(dto.getUpdatedBy());
    }

    @Test
    void getAdminConfigDoesNotQueryPromptsWhenPromptCodeBlank() {
        PostLangLabelGenConfigEntity entity = baseEntity();
        entity.setPromptCode("   ");
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(entity));

        PostLangLabelGenConfigDTO dto = service.getAdminConfig();

        assertNotNull(dto);
        assertEquals("   ", dto.getPromptCode());
        assertNull(dto.getModel());
        assertNull(dto.getProviderId());
        assertNull(dto.getEnableThinking());
        verify(promptsRepository, never()).findByPromptCode(anyString());
    }

    @Test
    void getAdminConfigDoesNotQueryPromptsWhenPromptCodeNull() {
        PostLangLabelGenConfigEntity entity = baseEntity();
        entity.setPromptCode(null);
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(entity));

        PostLangLabelGenConfigDTO dto = service.getAdminConfig();

        assertNotNull(dto);
        assertNull(dto.getPromptCode());
        assertNull(dto.getModel());
        assertNull(dto.getProviderId());
        assertNull(dto.getEnableThinking());
        verify(promptsRepository, never()).findByPromptCode(anyString());
    }

    @Test
    void getPublicConfigReturnsDefaultWhenMissing() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.empty());

        PostLangLabelGenPublicConfigDTO dto = service.getPublicConfig();

        assertNotNull(dto);
        assertTrue(Boolean.TRUE.equals(dto.getEnabled()));
        assertEquals(8000, dto.getMaxContentChars());
    }

    @Test
    void getPublicConfigTreatsNullEnabledAsFalse() {
        PostLangLabelGenConfigEntity entity = baseEntity();
        entity.setEnabled(null);
        entity.setMaxContentChars(999);
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(entity));

        PostLangLabelGenPublicConfigDTO dto = service.getPublicConfig();

        assertNotNull(dto);
        assertFalse(Boolean.TRUE.equals(dto.getEnabled()));
        assertEquals(999, dto.getMaxContentChars());
    }

    @Test
    void getPublicConfigUsesExistingEnabledFlagWhenTrue() {
        PostLangLabelGenConfigEntity entity = baseEntity();
        entity.setEnabled(Boolean.TRUE);
        entity.setMaxContentChars(777);
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(entity));

        PostLangLabelGenPublicConfigDTO dto = service.getPublicConfig();

        assertNotNull(dto);
        assertTrue(Boolean.TRUE.equals(dto.getEnabled()));
        assertEquals(777, dto.getMaxContentChars());
    }

    @Test
    void getConfigEntityOrDefaultReturnsDefaultWhenMissing() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.empty());

        PostLangLabelGenConfigEntity e = service.getConfigEntityOrDefault();

        assertNotNull(e);
        assertEquals("POST_LANG_LABEL", e.getGroupCode());
        assertEquals("DEFAULT", e.getSubType());
        assertTrue(Boolean.TRUE.equals(e.getEnabled()));
        assertEquals("LANG_DETECT", e.getPromptCode());
        assertEquals(8000, e.getMaxContentChars());
        assertEquals(0, e.getVersion());
    }

    @Test
    void getConfigEntityOrDefaultReturnsRepoEntityWhenExists() {
        PostLangLabelGenConfigEntity existing = baseEntity();
        existing.setPromptCode("P2");
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(existing));

        PostLangLabelGenConfigEntity got = service.getConfigEntityOrDefault();

        assertSame(existing, got);
    }

    @Test
    void upsertRejectsNullPayload() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(null, 1L, "u"));
        assertTrue(ex.getMessage().contains("payload"));
    }

    @Test
    void upsertRejectsNullPromptCode() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(baseEntity()));

        PostLangLabelGenConfigDTO payload = validPayload();
        payload.setPromptCode(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
        assertTrue(ex.getMessage().contains("promptCode"));
    }

    @Test
    void upsertRejectsBlankPromptCode() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(baseEntity()));

        PostLangLabelGenConfigDTO payload = validPayload();
        payload.setPromptCode("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
        assertTrue(ex.getMessage().contains("promptCode"));
    }

    @Test
    void upsertRejectsTooLongPromptCode() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(baseEntity()));

        PostLangLabelGenConfigDTO payload = validPayload();
        payload.setPromptCode("x".repeat(65));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
        assertTrue(ex.getMessage().contains("64"));
        verify(promptsRepository, never()).findByPromptCode(anyString());
    }

    @Test
    void upsertRejectsMaxContentCharsOutOfRangeLow() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(baseEntity()));

        PostLangLabelGenConfigDTO payload = validPayload();
        payload.setMaxContentChars(199);

        assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
    }

    @Test
    void upsertRejectsMaxContentCharsOutOfRangeHigh() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(baseEntity()));

        PostLangLabelGenConfigDTO payload = validPayload();
        payload.setMaxContentChars(100001);

        assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
    }

    @Test
    void upsertRejectsPromptCodeWhenNotExistsInPrompts() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(baseEntity()));
        when(promptsRepository.findByPromptCode("P404")).thenReturn(Optional.empty());

        PostLangLabelGenConfigDTO payload = validPayload();
        payload.setPromptCode("P404");
        payload.setMaxContentChars(8000);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
        assertTrue(ex.getMessage().contains("不存在"));
        assertTrue(ex.getMessage().contains("P404"));
    }

    @Test
    void upsertDefaultsMaxContentCharsWhenNullAndSavesMergedValues() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.empty());

        PromptsEntity prompt = promptEntity();
        when(promptsRepository.findByPromptCode("P1")).thenReturn(Optional.of(prompt));

        PostLangLabelGenConfigDTO payload = new PostLangLabelGenConfigDTO();
        payload.setPromptCode("P1");
        payload.setEnabled(null);
        payload.setMaxContentChars(null);

        PostLangLabelGenConfigDTO saved = service.upsertAdminConfig(payload, 99L, "alice");

        ArgumentCaptor<PostLangLabelGenConfigEntity> captor = ArgumentCaptor.forClass(PostLangLabelGenConfigEntity.class);
        verify(configRepository).save(captor.capture());
        PostLangLabelGenConfigEntity merged = captor.getValue();

        assertEquals("POST_LANG_LABEL", merged.getGroupCode());
        assertEquals("DEFAULT", merged.getSubType());
        assertEquals("P1", merged.getPromptCode());
        assertEquals(PostLangLabelGenConfigService.DEFAULT_MAX_CONTENT_CHARS, merged.getMaxContentChars());
        assertFalse(Boolean.TRUE.equals(merged.getEnabled()));
        assertEquals(99L, merged.getUpdatedBy());
        assertNotNull(merged.getUpdatedAt());

        assertEquals("alice", saved.getUpdatedBy());
        assertEquals(prompt.getModelName(), saved.getModel());
        assertEquals(prompt.getProviderId(), saved.getProviderId());
        assertEquals(prompt.getTemperature(), saved.getTemperature());
        assertEquals(prompt.getTopP(), saved.getTopP());
        assertEquals(prompt.getEnableDeepThinking(), saved.getEnableThinking());
        assertEquals(PostLangLabelGenConfigService.DEFAULT_MAX_CONTENT_CHARS, saved.getMaxContentChars());
    }

    @Test
    void upsertAllowsMaxContentCharsAtEdges() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(eq("POST_LANG_LABEL"), eq("DEFAULT")))
                .thenReturn(Optional.of(baseEntity()));

        when(promptsRepository.findByPromptCode("P1")).thenReturn(Optional.of(new PromptsEntity()));

        PostLangLabelGenConfigDTO payload1 = validPayload();
        payload1.setPromptCode("P1");
        payload1.setMaxContentChars(200);
        service.upsertAdminConfig(payload1, 1L, "u");

        PostLangLabelGenConfigDTO payload2 = validPayload();
        payload2.setPromptCode("P1");
        payload2.setMaxContentChars(100000);
        service.upsertAdminConfig(payload2, 1L, "u");
    }

    private static PostLangLabelGenConfigDTO validPayload() {
        PostLangLabelGenConfigDTO payload = new PostLangLabelGenConfigDTO();
        payload.setEnabled(Boolean.TRUE);
        payload.setPromptCode("P1");
        payload.setMaxContentChars(8000);
        return payload;
    }

    private static PostLangLabelGenConfigEntity baseEntity() {
        PostLangLabelGenConfigEntity e = new PostLangLabelGenConfigEntity();
        e.setGroupCode("POST_LANG_LABEL");
        e.setSubType("DEFAULT");
        e.setEnabled(Boolean.TRUE);
        e.setPromptCode("LANG_DETECT");
        e.setMaxContentChars(8000);
        e.setVersion(0);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(null);
        return e;
    }

    private static PromptsEntity promptEntity() {
        PromptsEntity p = new PromptsEntity();
        p.setModelName("gpt-4o-mini");
        p.setProviderId("provider-a");
        p.setTemperature(0.2);
        p.setTopP(0.8);
        p.setEnableDeepThinking(Boolean.TRUE);
        return p;
    }
}
