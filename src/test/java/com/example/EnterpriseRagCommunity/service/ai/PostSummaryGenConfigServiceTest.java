package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostSummaryGenConfigRepository;
import com.example.EnterpriseRagCommunity.repository.ai.PostSummaryGenHistoryRepository;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PostSummaryGenConfigServiceTest {

    @Test
    void getAdminConfigFallsBackToDefaultWhenMissing() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.empty());

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));
        PostSummaryGenConfigDTO dto = svc.getAdminConfig();

        assertNotNull(dto);
        assertEquals(Boolean.TRUE, dto.getEnabled());
        assertEquals(PostSummaryGenConfigService.DEFAULT_MAX_CONTENT_CHARS, dto.getMaxContentChars());
        assertEquals(PostSummaryGenConfigService.DEFAULT_PROMPT_CODE, dto.getPromptCode());
        assertNull(dto.getModel());
        assertNull(dto.getProviderId());
    }

    @Test
    void getAdminConfigReturnsExistingConfig() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);

        PostSummaryGenConfigEntity entity = new PostSummaryGenConfigEntity();
        entity.setId(10L);
        entity.setVersion(3);
        entity.setEnabled(Boolean.FALSE);
        entity.setMaxContentChars(3210);
        entity.setPromptCode("PCODE");
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(99L);

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.of(entity));

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));
        PostSummaryGenConfigDTO dto = svc.getAdminConfig();

        assertEquals(10L, dto.getId());
        assertEquals(3, dto.getVersion());
        assertEquals(Boolean.FALSE, dto.getEnabled());
        assertEquals(3210, dto.getMaxContentChars());
        assertEquals("PCODE", dto.getPromptCode());
    }

    @Test
    void getPublicConfigFallsBackToDefaultWhenMissing() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.empty());

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));
        PostSummaryGenPublicConfigDTO dto = svc.getPublicConfig();

        assertEquals(Boolean.TRUE, dto.getEnabled());
    }

    @Test
    void getPublicConfigRespectsEnabledFlag() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);

        PostSummaryGenConfigEntity entity = new PostSummaryGenConfigEntity();
        entity.setEnabled(Boolean.FALSE);

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.of(entity));

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));
        PostSummaryGenPublicConfigDTO dto = svc.getPublicConfig();

        assertEquals(Boolean.FALSE, dto.getEnabled());
    }

    @Test
    void getPublicConfigTreatsNullEnabledAsFalse() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);

        PostSummaryGenConfigEntity entity = new PostSummaryGenConfigEntity();
        entity.setEnabled(null);

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.of(entity));

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));
        PostSummaryGenPublicConfigDTO dto = svc.getPublicConfig();

        assertEquals(Boolean.FALSE, dto.getEnabled());
    }

    @Test
    void upsertAdminConfigRejectsNullPayload() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.upsertAdminConfig(null, 1L, "admin"));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("payload"));
    }

    @Test
    void upsertAdminConfigValidatesPromptCode() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.empty());

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));

        PostSummaryGenConfigDTO payload = new PostSummaryGenConfigDTO();
        payload.setPromptCode(" ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.upsertAdminConfig(payload, 1L, "admin"));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("promptCode"));

        payload.setPromptCode("a".repeat(65));
        ex = assertThrows(IllegalArgumentException.class,
                () -> svc.upsertAdminConfig(payload, 1L, "admin"));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("64"));

        payload.setPromptCode(null);
        ex = assertThrows(IllegalArgumentException.class,
                () -> svc.upsertAdminConfig(payload, 1L, "admin"));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("promptCode"));
    }

    @Test
    void upsertAdminConfigValidatesRangesAndDefaults() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(new PromptsEntity()));
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.empty());
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, promptsRepository);

        PostSummaryGenConfigDTO payload = new PostSummaryGenConfigDTO();
        payload.setPromptCode("SUMMARY_GEN");
        payload.setMaxContentChars(100);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.upsertAdminConfig(payload, 1L, "admin"));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("maxContentChars"));

        payload.setMaxContentChars(null);
        payload.setModel("  model-x  ");
        payload.setProviderId("  provider-1 ");
        payload.setEnabled(Boolean.TRUE);
        payload.setEnableThinking(Boolean.TRUE);

        PostSummaryGenConfigDTO dto = svc.upsertAdminConfig(payload, 7L, "admin");

        ArgumentCaptor<PostSummaryGenConfigEntity> cap = ArgumentCaptor.forClass(PostSummaryGenConfigEntity.class);
        verify(configRepository).save(cap.capture());
        PostSummaryGenConfigEntity saved = cap.getValue();

        assertEquals(PostSummaryGenConfigService.DEFAULT_MAX_CONTENT_CHARS, saved.getMaxContentChars());
        assertEquals(Boolean.TRUE, saved.getEnabled());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(7L, saved.getUpdatedBy());
        assertEquals(Boolean.TRUE, dto.getEnabled());
    }

    @Test
    void upsertAdminConfigRejectsTooLargeMaxContentChars() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.empty());
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(new PromptsEntity()));

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, promptsRepository);

        PostSummaryGenConfigDTO payload = new PostSummaryGenConfigDTO();
        payload.setPromptCode("SUMMARY_GEN");
        payload.setMaxContentChars(50001);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.upsertAdminConfig(payload, 1L, "admin"));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("maxContentChars"));
    }

    @Test
    void upsertAdminConfigAcceptsBoundaryMaxContentChars() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(new PromptsEntity()));

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, promptsRepository);

        PostSummaryGenConfigDTO minPayload = new PostSummaryGenConfigDTO();
        minPayload.setPromptCode("SUMMARY_GEN");
        minPayload.setMaxContentChars(200);
        minPayload.setEnabled(Boolean.TRUE);
        PostSummaryGenConfigDTO minDto = svc.upsertAdminConfig(minPayload, 2L, "admin");
        assertEquals(200, minDto.getMaxContentChars());

        PostSummaryGenConfigDTO maxPayload = new PostSummaryGenConfigDTO();
        maxPayload.setPromptCode("SUMMARY_GEN");
        maxPayload.setMaxContentChars(50000);
        maxPayload.setEnabled(Boolean.FALSE);
        PostSummaryGenConfigDTO maxDto = svc.upsertAdminConfig(maxPayload, 3L, "admin");
        assertEquals(50000, maxDto.getMaxContentChars());
    }

    @Test
    void upsertAdminConfigRejectsMissingPromptEntity() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.empty());
        when(promptsRepository.findByPromptCode("NOT_FOUND")).thenReturn(Optional.empty());

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, promptsRepository);

        PostSummaryGenConfigDTO payload = new PostSummaryGenConfigDTO();
        payload.setPromptCode("NOT_FOUND");
        payload.setMaxContentChars(4000);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.upsertAdminConfig(payload, 1L, "admin"));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("promptCode 不存在"));
    }

    @Test
    void listHistoryUsesSafePageAndSizeAndRoutesByPostId() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);

        PostSummaryGenHistoryEntity e = new PostSummaryGenHistoryEntity();
        e.setId(1L);
        e.setPostId(99L);
        e.setStatus("SUCCESS");
        e.setCreatedAt(LocalDateTime.now());
        e.setErrorMessage("ok");

        Page<PostSummaryGenHistoryEntity> page = new PageImpl<>(List.of(e));
        when(historyRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(page);
        when(historyRepository.findByPostIdOrderByCreatedAtDesc(anyLong(), any(Pageable.class))).thenReturn(page);

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));

        Page<PostSummaryGenHistoryDTO> p1 = svc.listHistory(null, -2, 1000);
        assertEquals(1, p1.getTotalElements());

        ArgumentCaptor<Pageable> pageableCap = ArgumentCaptor.forClass(Pageable.class);
        verify(historyRepository).findAllByOrderByCreatedAtDesc(pageableCap.capture());
        Pageable pageable = pageableCap.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(100, pageable.getPageSize());

        Page<PostSummaryGenHistoryDTO> p2 = svc.listHistory(99L, 2, 10);
        assertEquals(1, p2.getTotalElements());
        verify(historyRepository).findByPostIdOrderByCreatedAtDesc(eq(99L), any(Pageable.class));
    }

    @Test
    void recordHistorySkipsNull() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));
        svc.recordHistory(null);

        verifyNoInteractions(historyRepository);
    }

    @Test
    void recordHistoryPersistsNonNull() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));
        PostSummaryGenHistoryEntity e = new PostSummaryGenHistoryEntity();
        e.setPostId(1L);
        e.setStatus("SUCCESS");
        e.setCreatedAt(LocalDateTime.now());
        e.setAppliedMaxContentChars(4000);

        svc.recordHistory(e);

        verify(historyRepository).save(e);
    }

    @Test
    void getConfigEntityOrDefaultReturnsDefaultWhenMissing() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.empty());

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));
        PostSummaryGenConfigEntity cfg = svc.getConfigEntityOrDefault();

        assertEquals("POST_SUMMARY", cfg.getGroupCode());
        assertEquals("DEFAULT", cfg.getSubType());
        assertEquals(Boolean.TRUE, cfg.getEnabled());
        assertEquals(PostSummaryGenConfigService.DEFAULT_PROMPT_CODE, cfg.getPromptCode());
    }

    @Test
    void getConfigEntityOrDefaultReturnsExisting() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);

        PostSummaryGenConfigEntity entity = new PostSummaryGenConfigEntity();
        entity.setGroupCode("POST_SUMMARY");
        entity.setSubType("DEFAULT");
        entity.setEnabled(Boolean.FALSE);
        entity.setPromptCode("PCODE");

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.of(entity));

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, mock(PromptsRepository.class));
        PostSummaryGenConfigEntity cfg = svc.getConfigEntityOrDefault();

        assertEquals(Boolean.FALSE, cfg.getEnabled());
        assertEquals("PCODE", cfg.getPromptCode());
    }

    @Test
    void getAdminConfigWithBlankPromptCodeSkipsPromptLookup() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        PostSummaryGenConfigEntity entity = new PostSummaryGenConfigEntity();
        entity.setId(11L);
        entity.setVersion(1);
        entity.setPromptCode("   ");
        entity.setMaxContentChars(2500);
        entity.setEnabled(Boolean.TRUE);
        entity.setUpdatedAt(LocalDateTime.now());

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.of(entity));

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, promptsRepository);
        PostSummaryGenConfigDTO dto = svc.getAdminConfig();

        assertEquals("   ", dto.getPromptCode());
        assertNull(dto.getModel());
        assertNull(dto.getProviderId());
        verify(promptsRepository, never()).findByPromptCode(anyString());
    }

    @Test
    void getAdminConfigWithPromptCodeButPromptMissingMapsPromptFieldsToNull() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        PostSummaryGenConfigEntity entity = new PostSummaryGenConfigEntity();
        entity.setId(12L);
        entity.setVersion(2);
        entity.setPromptCode("SUMMARY_GEN");
        entity.setMaxContentChars(2600);
        entity.setEnabled(Boolean.TRUE);
        entity.setUpdatedAt(LocalDateTime.now());

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.of(entity));
        when(promptsRepository.findByPromptCode("SUMMARY_GEN")).thenReturn(Optional.empty());

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, promptsRepository);
        PostSummaryGenConfigDTO dto = svc.getAdminConfig();

        assertEquals("SUMMARY_GEN", dto.getPromptCode());
        assertNull(dto.getModel());
        assertNull(dto.getProviderId());
        assertNull(dto.getTemperature());
        assertNull(dto.getTopP());
        assertNull(dto.getEnableThinking());
    }

    @Test
    void getAdminConfigWithPromptCodeAndPromptEntityMapsPromptFields() {
        PostSummaryGenConfigRepository configRepository = mock(PostSummaryGenConfigRepository.class);
        PostSummaryGenHistoryRepository historyRepository = mock(PostSummaryGenHistoryRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        PostSummaryGenConfigEntity entity = new PostSummaryGenConfigEntity();
        entity.setId(13L);
        entity.setVersion(4);
        entity.setPromptCode("SUMMARY_GEN");
        entity.setMaxContentChars(2700);
        entity.setEnabled(Boolean.TRUE);
        entity.setUpdatedAt(LocalDateTime.now());

        PromptsEntity prompt = new PromptsEntity();
        prompt.setPromptCode("SUMMARY_GEN");
        prompt.setModelName("gpt-test");
        prompt.setProviderId("provider-x");
        prompt.setTemperature(0.7);
        prompt.setTopP(0.8);
        prompt.setEnableDeepThinking(Boolean.TRUE);

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("POST_SUMMARY", "DEFAULT"))
                .thenReturn(Optional.of(entity));
        when(promptsRepository.findByPromptCode("SUMMARY_GEN")).thenReturn(Optional.of(prompt));

        PostSummaryGenConfigService svc = new PostSummaryGenConfigService(configRepository, historyRepository, promptsRepository);
        PostSummaryGenConfigDTO dto = svc.getAdminConfig();

        assertEquals("gpt-test", dto.getModel());
        assertEquals("provider-x", dto.getProviderId());
        assertEquals(0.7, dto.getTemperature());
        assertEquals(0.8, dto.getTopP());
        assertEquals(Boolean.TRUE, dto.getEnableThinking());
    }
}
