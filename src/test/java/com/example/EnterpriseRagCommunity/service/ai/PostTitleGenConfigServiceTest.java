package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SuggestionKind;
import com.example.EnterpriseRagCommunity.repository.ai.PostSuggestionGenConfigRepository;
import com.example.EnterpriseRagCommunity.repository.ai.PostSuggestionGenHistoryRepository;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostTitleGenConfigServiceTest {

    @Test
    void getAdminConfigReturnsDefaultWhenMissing() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc(eq("POST_SUGGESTION"), eq(SuggestionKind.TITLE)))
                .thenReturn(Optional.empty());

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));

        PostTitleGenConfigDTO dto = service.getAdminConfig();

        assertNotNull(dto);
        assertTrue(Boolean.TRUE.equals(dto.getEnabled()));
        assertEquals("TITLE_GEN", dto.getPromptCode());
        assertEquals(5, dto.getDefaultCount());
        assertEquals(10, dto.getMaxCount());
        assertEquals(4000, dto.getMaxContentChars());
    }

    @Test
    void getPublicConfigUsesExistingEnabledFlag() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PostSuggestionGenConfigEntity existing = baseEntity();
        existing.setEnabled(Boolean.FALSE);
        existing.setDefaultCount(3);
        existing.setMaxCount(9);
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc(eq("POST_SUGGESTION"), eq(SuggestionKind.TITLE)))
                .thenReturn(Optional.of(existing));

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));

        PostTitleGenPublicConfigDTO dto = service.getPublicConfig();

        assertNotNull(dto);
        assertFalse(Boolean.TRUE.equals(dto.getEnabled()));
        assertEquals(3, dto.getDefaultCount());
        assertEquals(9, dto.getMaxCount());
    }

    @Test
    void getPublicConfigReturnsDefaultWhenMissing() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc(eq("POST_SUGGESTION"), eq(SuggestionKind.TITLE)))
                .thenReturn(Optional.empty());

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));

        PostTitleGenPublicConfigDTO dto = service.getPublicConfig();

        assertNotNull(dto);
        assertTrue(Boolean.TRUE.equals(dto.getEnabled()));
        assertEquals(5, dto.getDefaultCount());
        assertEquals(10, dto.getMaxCount());
    }

    @Test
    void upsertAdminConfigSavesMergedValues() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(new PromptsEntity()));
        PostSuggestionGenConfigEntity existing = baseEntity();
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc(eq("POST_SUGGESTION"), eq(SuggestionKind.TITLE)))
                .thenReturn(Optional.of(existing));
        when(configRepository.save(any(PostSuggestionGenConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, promptsRepository);
        PostTitleGenConfigDTO payload = validPayload();
        payload.setEnabled(null);
        payload.setModel("  gpt-4  ");
        payload.setProviderId("  provider-a  ");
        payload.setEnableThinking(null);

        PostTitleGenConfigDTO saved = service.upsertAdminConfig(payload, 99L, "alice");

        ArgumentCaptor<PostSuggestionGenConfigEntity> captor = ArgumentCaptor.forClass(PostSuggestionGenConfigEntity.class);
        verify(configRepository).save(captor.capture());
        PostSuggestionGenConfigEntity merged = captor.getValue();
        assertFalse(Boolean.TRUE.equals(merged.getEnabled()));
        assertEquals(99L, merged.getUpdatedBy());
        assertEquals("alice", saved.getUpdatedBy());
    }

    @Test
    void upsertAdminConfigFallsBackToDefaultsWhenNullableFieldsMissing() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(new PromptsEntity()));
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc(eq("POST_SUGGESTION"), eq(SuggestionKind.TITLE)))
                .thenReturn(Optional.empty());
        when(configRepository.save(any(PostSuggestionGenConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, promptsRepository);
        PostTitleGenConfigDTO payload = new PostTitleGenConfigDTO();
        payload.setPromptCode("TITLE_GEN");
        payload.setDefaultCount(null);
        payload.setMaxCount(null);
        payload.setMaxContentChars(null);

        PostTitleGenConfigDTO saved = service.upsertAdminConfig(payload, 1L, "root");

        assertEquals(5, saved.getDefaultCount());
        assertEquals(10, saved.getMaxCount());
        assertEquals(4000, saved.getMaxContentChars());
    }

    @Test
    void upsertRejectsNullPayload() {
        PostTitleGenConfigService service = newService();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(null, 1L, "u"));
        assertTrue(ex.getMessage().contains("payload"));
    }

    @Test
    void upsertRejectsBlankPromptCode() {
        PostTitleGenConfigService service = newService();
        PostTitleGenConfigDTO payload = validPayload();
        payload.setPromptCode("   ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
        assertTrue(ex.getMessage().contains("promptCode"));
    }

    @Test
    void upsertRejectsNullPromptCode() {
        PostTitleGenConfigService service = newService();
        PostTitleGenConfigDTO payload = validPayload();
        payload.setPromptCode(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
        assertTrue(ex.getMessage().contains("promptCode"));
    }

    @Test
    void upsertRejectsTooLongPromptCode() {
        PostTitleGenConfigService service = newService();
        PostTitleGenConfigDTO payload = validPayload();
        payload.setPromptCode("x".repeat(65));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
        assertTrue(ex.getMessage().contains("64"));
    }

    @Test
    void upsertRejectsPromptCodeWhenNotExists() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc(eq("POST_SUGGESTION"), eq(SuggestionKind.TITLE)))
                .thenReturn(Optional.of(baseEntity()));
        when(promptsRepository.findByPromptCode("P404")).thenReturn(Optional.empty());

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, promptsRepository);
        PostTitleGenConfigDTO payload = validPayload();
        payload.setPromptCode("P404");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
        assertTrue(ex.getMessage().contains("不存在"));
        assertTrue(ex.getMessage().contains("P404"));
    }

    @Test
    void upsertRejectsDefaultCountOutOfRange() {
        PostTitleGenConfigService service = newService();
        PostTitleGenConfigDTO payload = validPayload();
        payload.setDefaultCount(0);
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
    }

    @Test
    void upsertRejectsMaxCountOutOfRange() {
        PostTitleGenConfigService service = newService();
        PostTitleGenConfigDTO payload = validPayload();
        payload.setMaxCount(51);
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
    }

    @Test
    void upsertRejectsMaxCountLowerBound() {
        PostTitleGenConfigService service = newService();
        PostTitleGenConfigDTO payload = validPayload();
        payload.setMaxCount(0);
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
    }

    @Test
    void upsertRejectsDefaultCountUpperBound() {
        PostTitleGenConfigService service = newService();
        PostTitleGenConfigDTO payload = validPayload();
        payload.setDefaultCount(51);
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
    }

    @Test
    void upsertRejectsDefaultCountGreaterThanMaxCount() {
        PostTitleGenConfigService service = newService();
        PostTitleGenConfigDTO payload = validPayload();
        payload.setDefaultCount(10);
        payload.setMaxCount(9);
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
    }

    @Test
    void upsertRejectsMaxContentCharsOutOfRange() {
        PostTitleGenConfigService service = newService();
        PostTitleGenConfigDTO payload = validPayload();
        payload.setMaxContentChars(199);
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
    }

    @Test
    void upsertRejectsMaxContentCharsUpperBound() {
        PostTitleGenConfigService service = newService();
        PostTitleGenConfigDTO payload = validPayload();
        payload.setMaxContentChars(50001);
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));
    }



    @Test
    void upsertRejectsInvalidHistoryDaysAndRows() {
        PostTitleGenConfigService service = newService();
        PostTitleGenConfigDTO payload1 = validPayload();
        payload1.setHistoryKeepDays(0);
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload1, 1L, "u"));

        PostTitleGenConfigDTO payload2 = validPayload();
        payload2.setHistoryKeepRows(0);
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload2, 1L, "u"));
    }

    @Test
    void upsertAcceptsBoundaryValuesForCountAndContentChars() {
        PostTitleGenConfigService lowerService = newService();
        PostTitleGenConfigDTO lowerPayload = validPayload();
        lowerPayload.setDefaultCount(1);
        lowerPayload.setMaxCount(1);
        lowerPayload.setMaxContentChars(200);
        PostTitleGenConfigDTO lowerSaved = lowerService.upsertAdminConfig(lowerPayload, 1L, "u");
        assertEquals(1, lowerSaved.getDefaultCount());
        assertEquals(1, lowerSaved.getMaxCount());
        assertEquals(200, lowerSaved.getMaxContentChars());

        PostTitleGenConfigService upperService = newService();
        PostTitleGenConfigDTO upperPayload = validPayload();
        upperPayload.setDefaultCount(50);
        upperPayload.setMaxCount(50);
        upperPayload.setMaxContentChars(50000);
        PostTitleGenConfigDTO upperSaved = upperService.upsertAdminConfig(upperPayload, 2L, "u2");
        assertEquals(50, upperSaved.getDefaultCount());
        assertEquals(50, upperSaved.getMaxCount());
        assertEquals(50000, upperSaved.getMaxContentChars());
    }

    @Test
    void getAdminConfigSkipsPromptLookupWhenPromptCodeBlank() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        PostSuggestionGenConfigEntity existing = baseEntity();
        existing.setPromptCode("   ");
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc(eq("POST_SUGGESTION"), eq(SuggestionKind.TITLE)))
                .thenReturn(Optional.of(existing));

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, promptsRepository);

        PostTitleGenConfigDTO dto = service.getAdminConfig();

        verify(promptsRepository, never()).findByPromptCode(anyString());
        assertNull(dto.getModel());
        assertNull(dto.getProviderId());
        assertNull(dto.getTemperature());
        assertNull(dto.getTopP());
        assertNull(dto.getEnableThinking());
    }

    @Test
    void getAdminConfigSkipsPromptLookupWhenPromptCodeNull() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        PostSuggestionGenConfigEntity existing = baseEntity();
        existing.setPromptCode(null);
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc(eq("POST_SUGGESTION"), eq(SuggestionKind.TITLE)))
                .thenReturn(Optional.of(existing));

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, promptsRepository);

        PostTitleGenConfigDTO dto = service.getAdminConfig();

        verify(promptsRepository, never()).findByPromptCode(anyString());
        assertNull(dto.getModel());
        assertNull(dto.getProviderId());
        assertNull(dto.getTemperature());
        assertNull(dto.getTopP());
        assertNull(dto.getEnableThinking());
    }

    @Test
    void getAdminConfigReturnsNullPromptFieldsWhenPromptNotFound() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        PostSuggestionGenConfigEntity existing = baseEntity();
        existing.setPromptCode("TITLE_GEN");
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc(eq("POST_SUGGESTION"), eq(SuggestionKind.TITLE)))
                .thenReturn(Optional.of(existing));
        when(promptsRepository.findByPromptCode("TITLE_GEN")).thenReturn(Optional.empty());

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, promptsRepository);

        PostTitleGenConfigDTO dto = service.getAdminConfig();

        assertNull(dto.getModel());
        assertNull(dto.getProviderId());
        assertNull(dto.getTemperature());
        assertNull(dto.getTopP());
        assertNull(dto.getEnableThinking());
    }

    @Test
    void listHistoryUsesGlobalQueryAndMapsJsonFields() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PostSuggestionGenHistoryEntity row = new PostSuggestionGenHistoryEntity();
        row.setId(100L);
        row.setUserId(8L);
        row.setCreatedAt(LocalDateTime.now());
        row.setBoardName("board");
        row.setInputTagsJson(Arrays.asList(" A ", "", null, 7));
        row.setOutputJson(Map.of("titles", Arrays.asList(" T1 ", "", null, 3)));
        row.setRequestedCount(2);
        row.setAppliedMaxContentChars(1000);
        row.setContentLen(20);
        row.setContentExcerpt("excerpt");

        Page<PostSuggestionGenHistoryEntity> pageRows = new PageImpl<>(List.of(row));
        when(historyRepository.findByKindOrderByCreatedAtDesc(eq(SuggestionKind.TITLE), any(Pageable.class)))
                .thenReturn(pageRows);

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));

        Page<PostTitleGenHistoryDTO> page = service.listHistory(null, -1, 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(historyRepository).findByKindOrderByCreatedAtDesc(eq(SuggestionKind.TITLE), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(1, pageable.getPageSize());

        PostTitleGenHistoryDTO dto = page.getContent().get(0);
        assertEquals(List.of("A", "7"), dto.getTags());
        assertEquals(List.of("T1", "3"), dto.getTitles());
    }

    @Test
    void listHistoryUsesUserQueryAndClampsLargePageSize() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PostSuggestionGenHistoryEntity row = new PostSuggestionGenHistoryEntity();
        row.setInputTagsJson(null);
        row.setOutputJson("scalar");
        when(historyRepository.findByKindAndUserIdOrderByCreatedAtDesc(eq(SuggestionKind.TITLE), eq(9L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));

        Page<PostTitleGenHistoryDTO> page = service.listHistory(9L, 3, 999);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(historyRepository).findByKindAndUserIdOrderByCreatedAtDesc(eq(SuggestionKind.TITLE), eq(9L), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(3, pageable.getPageNumber());
        assertEquals(100, pageable.getPageSize());

        PostTitleGenHistoryDTO dto = page.getContent().get(0);
        assertEquals(List.of(), dto.getTags());
        assertEquals(List.of(), dto.getTitles());
    }

    @Test
    void listHistoryReturnsEmptyTitlesWhenMapTitlesIsNotList() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PostSuggestionGenHistoryEntity row = new PostSuggestionGenHistoryEntity();
        row.setOutputJson(Map.of("titles", "not-a-list"));
        when(historyRepository.findByKindOrderByCreatedAtDesc(eq(SuggestionKind.TITLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));

        Page<PostTitleGenHistoryDTO> page = service.listHistory(null, 0, 10);

        assertEquals(List.of(), page.getContent().get(0).getTitles());
    }

    @Test
    void recordHistorySkipsNullInput() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));

        service.recordHistory(null);

        verify(historyRepository, never()).save(any(PostSuggestionGenHistoryEntity.class));
    }

    @Test
    void recordHistorySavesNonNullEntity() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));
        PostSuggestionGenHistoryEntity entity = new PostSuggestionGenHistoryEntity();

        service.recordHistory(entity);

        verify(historyRepository).save(entity);
    }

    @Test
    void getConfigEntityOrDefaultReturnsExistingEntity() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PostSuggestionGenConfigEntity existing = baseEntity();
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc(eq("POST_SUGGESTION"), eq(SuggestionKind.TITLE)))
                .thenReturn(Optional.of(existing));

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));

        PostSuggestionGenConfigEntity resolved = service.getConfigEntityOrDefault();

        assertEquals(existing, resolved);
    }

    @Test
    void getConfigEntityOrDefaultReturnsDefaultWhenMissing() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc(eq("POST_SUGGESTION"), eq(SuggestionKind.TITLE)))
                .thenReturn(Optional.empty());

        PostTitleGenConfigService service = new PostTitleGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));

        PostSuggestionGenConfigEntity resolved = service.getConfigEntityOrDefault();

        assertEquals("TITLE_GEN", resolved.getPromptCode());
        assertEquals(5, resolved.getDefaultCount());
        assertEquals(10, resolved.getMaxCount());
        assertEquals(4000, resolved.getMaxContentChars());
    }

    private static PostTitleGenConfigService newService() {
        PostSuggestionGenConfigRepository configRepository = mock(PostSuggestionGenConfigRepository.class);
        PostSuggestionGenHistoryRepository historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(new PromptsEntity()));
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc(eq("POST_SUGGESTION"), eq(SuggestionKind.TITLE)))
                .thenReturn(Optional.of(baseEntity()));
        when(configRepository.save(any(PostSuggestionGenConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return new PostTitleGenConfigService(configRepository, historyRepository, promptsRepository);
    }

    private static PostSuggestionGenConfigEntity baseEntity() {
        PostSuggestionGenConfigEntity entity = new PostSuggestionGenConfigEntity();
        entity.setGroupCode("POST_SUGGESTION");
        entity.setKind(SuggestionKind.TITLE);
        entity.setEnabled(Boolean.TRUE);
        entity.setPromptCode("TITLE_GEN");
        entity.setDefaultCount(5);
        entity.setMaxCount(10);
        entity.setMaxContentChars(4000);
        entity.setHistoryEnabled(Boolean.TRUE);
        entity.setHistoryKeepDays(30);
        entity.setHistoryKeepRows(1000);
        entity.setVersion(1);
        entity.setUpdatedAt(LocalDateTime.now().minusDays(1));
        entity.setUpdatedBy(1L);
        return entity;
    }

    private static PostTitleGenConfigDTO validPayload() {
        PostTitleGenConfigDTO dto = new PostTitleGenConfigDTO();
        dto.setEnabled(Boolean.TRUE);
        dto.setPromptCode("TITLE_GEN");
        dto.setModel("model-z");
        dto.setProviderId("provider-z");
        dto.setTemperature(1.1);
        dto.setTopP(0.9);
        dto.setEnableThinking(Boolean.TRUE);
        dto.setDefaultCount(5);
        dto.setMaxCount(10);
        dto.setMaxContentChars(5000);
        dto.setHistoryEnabled(Boolean.TRUE);
        dto.setHistoryKeepDays(7);
        dto.setHistoryKeepRows(10);
        return dto;
    }
}
