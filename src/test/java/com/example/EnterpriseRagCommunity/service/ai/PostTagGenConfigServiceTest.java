package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SuggestionKind;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostSuggestionGenConfigRepository;
import com.example.EnterpriseRagCommunity.repository.ai.PostSuggestionGenHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
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

public class PostTagGenConfigServiceTest {

    private PostSuggestionGenConfigRepository configRepository;
    private PostSuggestionGenHistoryRepository historyRepository;
    private com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository promptsRepository;
    private PostTagGenConfigService service;

    @BeforeEach
    void setUp() {
        configRepository = mock(PostSuggestionGenConfigRepository.class);
        historyRepository = mock(PostSuggestionGenHistoryRepository.class);
        promptsRepository = mock(com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(new PromptsEntity()));
        service = new PostTagGenConfigService(configRepository, historyRepository, promptsRepository);
    }

    @Test
    void getAdminConfig_returnsRepoEntity_whenExists() {
        PostSuggestionGenConfigEntity entity = sampleConfigEntity();
        entity.setPromptCode("TAG_GEN_CUSTOM");
        PromptsEntity prompt = new PromptsEntity();
        prompt.setModelName("m1");
        prompt.setProviderId("p1");
        prompt.setTemperature(0.3);
        prompt.setTopP(0.7);
        prompt.setEnableDeepThinking(Boolean.TRUE);
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(entity));
        when(promptsRepository.findByPromptCode("TAG_GEN_CUSTOM")).thenReturn(Optional.of(prompt));

        PostTagGenConfigDTO dto = service.getAdminConfig();

        assertEquals("TAG_GEN_CUSTOM", dto.getPromptCode());
        assertEquals(entity.getDefaultCount(), dto.getDefaultCount());
        assertEquals(entity.getMaxCount(), dto.getMaxCount());
        assertEquals(entity.getMaxContentChars(), dto.getMaxContentChars());
        assertEquals("m1", dto.getModel());
        assertEquals("p1", dto.getProviderId());
        assertEquals(0.3, dto.getTemperature());
        assertEquals(0.7, dto.getTopP());
        assertTrue(dto.getEnableThinking());
    }

    @Test
    void getAdminConfig_returnsDefault_whenMissing() {
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.empty());

        PostTagGenConfigDTO dto = service.getAdminConfig();

        assertEquals("TAG_GEN", dto.getPromptCode());
        assertEquals(5, dto.getDefaultCount());
        assertEquals(10, dto.getMaxCount());
        assertEquals(4000, dto.getMaxContentChars());
        assertTrue(Boolean.TRUE.equals(dto.getEnabled()));
    }

    @Test
    void getAdminConfig_skipsPromptLookup_whenPromptCodeBlank() {
        PostSuggestionGenConfigEntity entity = sampleConfigEntity();
        entity.setPromptCode("   ");
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(entity));

        PostTagGenConfigDTO dto = service.getAdminConfig();

        assertNull(dto.getModel());
        assertNull(dto.getProviderId());
        assertNull(dto.getTemperature());
        assertNull(dto.getTopP());
        assertNull(dto.getEnableThinking());
        verify(promptsRepository, never()).findByPromptCode(anyString());
    }

    @Test
    void getAdminConfig_setsPromptDerivedFieldsNull_whenPromptNotFound() {
        PostSuggestionGenConfigEntity entity = sampleConfigEntity();
        entity.setPromptCode("TAG_GEN_MISS");
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(entity));
        when(promptsRepository.findByPromptCode("TAG_GEN_MISS")).thenReturn(Optional.empty());

        PostTagGenConfigDTO dto = service.getAdminConfig();

        assertNull(dto.getModel());
        assertNull(dto.getProviderId());
        assertNull(dto.getTemperature());
        assertNull(dto.getTopP());
        assertNull(dto.getEnableThinking());
    }

    @Test
    void getPublicConfig_returnsRepoEntity_whenExists() {
        PostSuggestionGenConfigEntity entity = sampleConfigEntity();
        entity.setEnabled(Boolean.FALSE);
        entity.setDefaultCount(7);
        entity.setMaxCount(13);
        entity.setMaxContentChars(2300);
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(entity));

        PostTagGenPublicConfigDTO dto = service.getPublicConfig();

        assertFalse(dto.getEnabled());
        assertEquals(7, dto.getDefaultCount());
        assertEquals(13, dto.getMaxCount());
        assertEquals(2300, dto.getMaxContentChars());
    }

    @Test
    void getPublicConfig_returnsDefault_whenMissing() {
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.empty());

        PostTagGenPublicConfigDTO dto = service.getPublicConfig();

        assertTrue(dto.getEnabled());
        assertEquals(5, dto.getDefaultCount());
        assertEquals(10, dto.getMaxCount());
        assertEquals(4000, dto.getMaxContentChars());
    }

    @Test
    void getConfigEntityOrDefault_returnsRepoEntity_whenExists() {
        PostSuggestionGenConfigEntity entity = sampleConfigEntity();
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(entity));

        PostSuggestionGenConfigEntity got = service.getConfigEntityOrDefault();

        assertEquals(entity, got);
    }

    @Test
    void getConfigEntityOrDefault_returnsDefault_whenMissing() {
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.empty());

        PostSuggestionGenConfigEntity got = service.getConfigEntityOrDefault();

        assertEquals("TAG_GEN", got.getPromptCode());
        assertEquals(5, got.getDefaultCount());
        assertEquals(10, got.getMaxCount());
        assertEquals(4000, got.getMaxContentChars());
    }

    @Test
    void upsertAdminConfig_updatesExistingAndNormalizesFields() {
        PostSuggestionGenConfigEntity existing = sampleConfigEntity();
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(existing));
        when(configRepository.save(any(PostSuggestionGenConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostTagGenConfigDTO payload = validPayload();
        payload.setModel("  gpt-4o-mini  ");
        payload.setProviderId("   ");
        payload.setEnabled(null);
        payload.setEnableThinking(null);
        payload.setHistoryEnabled(null);

        PostTagGenConfigDTO out = service.upsertAdminConfig(payload, 88L, "alice");

        ArgumentCaptor<PostSuggestionGenConfigEntity> captor = ArgumentCaptor.forClass(PostSuggestionGenConfigEntity.class);
        verify(configRepository).save(captor.capture());
        PostSuggestionGenConfigEntity saved = captor.getValue();

        assertFalse(saved.getEnabled());
        assertFalse(saved.getHistoryEnabled());
        assertEquals(88L, saved.getUpdatedBy());
        assertNotNull(saved.getUpdatedAt());
        assertEquals("alice", out.getUpdatedBy());
    }

    @Test
    void upsertAdminConfig_createsDefaultThenUpdates_whenNoExistingConfig() {
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.empty());
        when(configRepository.save(any(PostSuggestionGenConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostTagGenConfigDTO out = service.upsertAdminConfig(validPayload(), 9L, "bob");

        assertEquals("TAG_GEN", out.getPromptCode());
        assertEquals(5, out.getDefaultCount());
        assertEquals(10, out.getMaxCount());
        assertEquals(1200, out.getMaxContentChars());
    }

    @Test
    void upsertAdminConfig_appliesCountDefaults_whenPayloadCountsAreNull() {
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(sampleConfigEntity()));
        when(configRepository.save(any(PostSuggestionGenConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostTagGenConfigDTO payload = validPayload();
        payload.setDefaultCount(null);
        payload.setMaxCount(null);
        payload.setMaxContentChars(null);

        PostTagGenConfigDTO out = service.upsertAdminConfig(payload, 1L, "u");

        assertEquals(5, out.getDefaultCount());
        assertEquals(10, out.getMaxCount());
        assertEquals(4000, out.getMaxContentChars());
    }

    @Test
    void upsertAdminConfig_throwsWhenPayloadNull() {
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(sampleConfigEntity()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(null, 1L, "u"));

        assertTrue(ex.getMessage().contains("payload"));
    }

    @Test
    void upsertAdminConfig_throwsWhenPromptCodeBlank() {
        assertValidationError(payload -> payload.setPromptCode("  "), "promptCode 不能为空");
    }

    @Test
    void upsertAdminConfig_throwsWhenPromptCodeNull() {
        assertValidationError(payload -> payload.setPromptCode(null), "promptCode 不能为空");
    }

    @Test
    void upsertAdminConfig_throwsWhenPromptCodeTooLong() {
        assertValidationError(payload -> payload.setPromptCode("A".repeat(65)), "promptCode 长度不能超过 64");
    }

    @Test
    void upsertAdminConfig_throwsWhenPromptCodeNotFound() {
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(sampleConfigEntity()));
        when(promptsRepository.findByPromptCode("TAG_GEN_MISSING")).thenReturn(Optional.empty());

        PostTagGenConfigDTO payload = validPayload();
        payload.setPromptCode("TAG_GEN_MISSING");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));

        assertTrue(ex.getMessage().contains("promptCode 不存在"));
    }

    @Test
    void upsertAdminConfig_throwsWhenDefaultCountOutOfRange() {
        assertValidationError(payload -> payload.setDefaultCount(0), "defaultCount 需在 [1,50] 范围内");
        assertValidationError(payload -> payload.setDefaultCount(51), "defaultCount 需在 [1,50] 范围内");
    }

    @Test
    void upsertAdminConfig_throwsWhenMaxCountOutOfRange() {
        assertValidationError(payload -> payload.setMaxCount(0), "maxCount 需在 [1,50] 范围内");
        assertValidationError(payload -> payload.setMaxCount(51), "maxCount 需在 [1,50] 范围内");
    }

    @Test
    void upsertAdminConfig_allowsNullableTuningAndHistoryRetentionFields() {
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(sampleConfigEntity()));
        when(configRepository.save(any(PostSuggestionGenConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostTagGenConfigDTO payload = validPayload();
        payload.setTemperature(null);
        payload.setTopP(null);
        payload.setHistoryKeepDays(null);
        payload.setHistoryKeepRows(null);

        PostTagGenConfigDTO out = service.upsertAdminConfig(payload, 2L, "u2");

        assertNull(out.getTemperature());
        assertNull(out.getTopP());
        assertNull(out.getHistoryKeepDays());
        assertNull(out.getHistoryKeepRows());
    }

    @Test
    void upsertAdminConfig_setsModelAndProviderIdNull_whenInputNull() {
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(sampleConfigEntity()));
        when(configRepository.save(any(PostSuggestionGenConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostTagGenConfigDTO payload = validPayload();
        payload.setModel(null);
        payload.setProviderId(null);

        PostTagGenConfigDTO out = service.upsertAdminConfig(payload, 3L, "u3");

        assertNull(out.getModel());
        assertNull(out.getProviderId());
    }

    @Test
    void upsertAdminConfig_setsModelNull_whenInputBlank() {
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(sampleConfigEntity()));
        when(configRepository.save(any(PostSuggestionGenConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostTagGenConfigDTO payload = validPayload();
        payload.setModel("   ");

        PostTagGenConfigDTO out = service.upsertAdminConfig(payload, 4L, "u4");

        assertNull(out.getModel());
    }

    @Test
    void upsertAdminConfig_throwsWhenDefaultCountGreaterThanMaxCount() {
        assertValidationError(payload -> {
            payload.setDefaultCount(11);
            payload.setMaxCount(10);
        }, "defaultCount 不能大于 maxCount");
    }

    @Test
    void upsertAdminConfig_throwsWhenMaxContentCharsOutOfRange() {
        assertValidationError(payload -> payload.setMaxContentChars(199), "maxContentChars 需在 [200,50000] 范围内");
        assertValidationError(payload -> payload.setMaxContentChars(50001), "maxContentChars 需在 [200,50000] 范围内");
    }

    @Test
    void upsertAdminConfig_throwsWhenHistoryKeepDaysNotPositive() {
        assertValidationError(payload -> payload.setHistoryKeepDays(0), "historyKeepDays 必须为正数");
    }

    @Test
    void upsertAdminConfig_throwsWhenHistoryKeepRowsNotPositive() {
        assertValidationError(payload -> payload.setHistoryKeepRows(0), "historyKeepRows 必须为正数");
    }

    @Test
    void listHistory_sanitizesPageAndSize_andUsesGlobalQuery_whenUserIdIsNull() {
        PostSuggestionGenHistoryEntity row = sampleHistoryEntity();
        row.setOutputJson(java.util.Arrays.asList("  tagA  ", "", null, "tagB"));
        when(historyRepository.findByKindOrderByCreatedAtDesc(eq(SuggestionKind.TOPIC_TAG), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        Page<PostTagGenHistoryDTO> result = service.listHistory(null, -5, 999);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(historyRepository).findByKindOrderByCreatedAtDesc(eq(SuggestionKind.TOPIC_TAG), pageableCaptor.capture());
        Pageable used = pageableCaptor.getValue();
        assertEquals(0, used.getPageNumber());
        assertEquals(100, used.getPageSize());

        assertEquals(1, result.getTotalElements());
        assertEquals(List.of("tagA", "tagB"), result.getContent().get(0).getTags());
    }

    @Test
    void listHistory_usesUserQuery_whenUserIdProvided() {
        PostSuggestionGenHistoryEntity row = sampleHistoryEntity();
        row.setOutputJson(Map.of("tags", java.util.Arrays.asList(" x ", "", null, "y")));

        when(historyRepository.findByKindAndUserIdOrderByCreatedAtDesc(eq(SuggestionKind.TOPIC_TAG), eq(42L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        Page<PostTagGenHistoryDTO> result = service.listHistory(42L, 2, 20);

        verify(historyRepository).findByKindAndUserIdOrderByCreatedAtDesc(eq(SuggestionKind.TOPIC_TAG), eq(42L), any(Pageable.class));
        assertEquals(List.of("x", "y"), result.getContent().get(0).getTags());
    }

    @Test
    void listHistory_returnsEmptyTags_forUnsupportedOutputJsonShapes() {
        PostSuggestionGenHistoryEntity row1 = sampleHistoryEntity();
        row1.setOutputJson(null);
        PostSuggestionGenHistoryEntity row2 = sampleHistoryEntity();
        row2.setOutputJson(Map.of("tags", "bad-shape"));
        PostSuggestionGenHistoryEntity row3 = sampleHistoryEntity();
        row3.setOutputJson("raw-text");

        when(historyRepository.findByKindOrderByCreatedAtDesc(eq(SuggestionKind.TOPIC_TAG), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row1, row2, row3)));

        Page<PostTagGenHistoryDTO> result = service.listHistory(null, 0, 3);

        assertEquals(List.of(), result.getContent().get(0).getTags());
        assertEquals(List.of(), result.getContent().get(1).getTags());
        assertEquals(List.of(), result.getContent().get(2).getTags());
    }

    @Test
    void recordHistory_ignoresNullInput() {
        service.recordHistory(null);
        verify(historyRepository, never()).save(any(PostSuggestionGenHistoryEntity.class));
    }

    @Test
    void recordHistory_savesEntity_whenInputNotNull() {
        PostSuggestionGenHistoryEntity entity = sampleHistoryEntity();

        service.recordHistory(entity);

        verify(historyRepository).save(entity);
    }

    private void assertValidationError(java.util.function.Consumer<PostTagGenConfigDTO> mutator, String expectedMessagePart) {
        when(configRepository.findTopByGroupCodeAndKindOrderByUpdatedAtDesc("POST_SUGGESTION", SuggestionKind.TOPIC_TAG))
                .thenReturn(Optional.of(sampleConfigEntity()));

        PostTagGenConfigDTO payload = validPayload();
        mutator.accept(payload);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.upsertAdminConfig(payload, 1L, "u"));

        assertTrue(ex.getMessage().contains(expectedMessagePart));
    }

    private PostTagGenConfigDTO validPayload() {
        PostTagGenConfigDTO dto = new PostTagGenConfigDTO();
        dto.setEnabled(Boolean.TRUE);
        dto.setPromptCode("TAG_GEN");
        dto.setModel("model-a");
        dto.setProviderId("provider-a");
        dto.setTemperature(0.5);
        dto.setTopP(0.9);
        dto.setEnableThinking(Boolean.TRUE);
        dto.setDefaultCount(5);
        dto.setMaxCount(10);
        dto.setMaxContentChars(1200);
        dto.setHistoryEnabled(Boolean.TRUE);
        dto.setHistoryKeepDays(7);
        dto.setHistoryKeepRows(100);
        return dto;
    }

    private PostSuggestionGenConfigEntity sampleConfigEntity() {
        PostSuggestionGenConfigEntity entity = new PostSuggestionGenConfigEntity();
        entity.setId(1L);
        entity.setVersion(0);
        entity.setGroupCode("POST_SUGGESTION");
        entity.setKind(SuggestionKind.TOPIC_TAG);
        entity.setEnabled(Boolean.TRUE);
        entity.setPromptCode("TAG_GEN");
        entity.setDefaultCount(5);
        entity.setMaxCount(10);
        entity.setMaxContentChars(4000);
        entity.setHistoryEnabled(Boolean.TRUE);
        entity.setHistoryKeepDays(30);
        entity.setHistoryKeepRows(5000);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(1L);
        return entity;
    }

    private PostSuggestionGenHistoryEntity sampleHistoryEntity() {
        PostSuggestionGenHistoryEntity entity = new PostSuggestionGenHistoryEntity();
        entity.setId(3L);
        entity.setKind(SuggestionKind.TOPIC_TAG);
        entity.setUserId(42L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setBoardName("board");
        entity.setTitleExcerpt("title");
        entity.setRequestedCount(5);
        entity.setAppliedMaxContentChars(4000);
        entity.setContentLen(120);
        entity.setContentExcerpt("content");
        return entity;
    }
}
