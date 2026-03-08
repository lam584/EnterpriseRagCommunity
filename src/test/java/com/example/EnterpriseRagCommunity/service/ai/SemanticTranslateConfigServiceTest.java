package com.example.EnterpriseRagCommunity.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslatePublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.SemanticTranslateConfigRepository;
import com.example.EnterpriseRagCommunity.repository.ai.SemanticTranslateHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class SemanticTranslateConfigServiceTest {

    private SemanticTranslateConfigRepository configRepository;
    private SemanticTranslateHistoryRepository historyRepository;
    private PromptsRepository promptsRepository;
    private SupportedLanguageService supportedLanguageService;
    private ObjectMapper objectMapper;
    private SemanticTranslateConfigService service;

    @BeforeEach
    void setUp() {
        configRepository = mock(SemanticTranslateConfigRepository.class);
        historyRepository = mock(SemanticTranslateHistoryRepository.class);
        promptsRepository = mock(PromptsRepository.class);
        when(promptsRepository.findByPromptCode(anyString())).thenReturn(Optional.of(new PromptsEntity()));
        supportedLanguageService = mock(SupportedLanguageService.class);
        objectMapper = new ObjectMapper();
        service = new SemanticTranslateConfigService(configRepository, historyRepository, promptsRepository, objectMapper, supportedLanguageService);

        when(supportedLanguageService.listActiveLanguageCodes()).thenReturn(List.of("zh-CN", "en"));
        when(supportedLanguageService.normalizeToLanguageCode(any())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0, String.class);
            return value == null ? SupportedLanguageService.DEFAULT_LANGUAGE_CODE : value.trim();
        });
    }

    @Test
    void getAdminConfig_whenConfigMissing_shouldReturnDefaultDto() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.empty());

        SemanticTranslateConfigDTO dto = service.getAdminConfig();

        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getPromptCode()).isEqualTo(SemanticTranslateConfigService.DEFAULT_PROMPT_CODE);
        assertThat(dto.getMaxContentChars()).isEqualTo(SemanticTranslateConfigService.DEFAULT_MAX_CONTENT_CHARS);
        assertThat(dto.getAllowedTargetLanguages()).containsExactly("zh-CN", "en");
    }

    @Test
    void getAdminConfig_whenPresentAndAllowedBlank_shouldFallbackToDefaultLanguages() {
        SemanticTranslateConfigEntity entity = new SemanticTranslateConfigEntity();
        entity.setEnabled(Boolean.TRUE);
        entity.setPromptCode("PROMPT_A");
        entity.setAllowedTargetLangs("   ");
        entity.setMaxContentChars(5000);
        entity.setUpdatedAt(LocalDateTime.now());

        when(supportedLanguageService.listActiveLanguageCodes()).thenReturn(List.of("ja", "en"));
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(entity));

        SemanticTranslateConfigDTO dto = service.getAdminConfig();

        assertThat(dto.getPromptCode()).isEqualTo("PROMPT_A");
        assertThat(dto.getAllowedTargetLanguages()).containsExactly("ja", "en");
    }

    @Test
    void getAdminConfig_whenPromptFound_shouldMapPromptFields() {
        SemanticTranslateConfigEntity entity = new SemanticTranslateConfigEntity();
        entity.setEnabled(Boolean.TRUE);
        entity.setPromptCode("PROMPT_A");
        entity.setAllowedTargetLangs("[\"zh-CN\"]");
        entity.setMaxContentChars(5000);
        entity.setUpdatedAt(LocalDateTime.now());

        PromptsEntity prompt = new PromptsEntity();
        prompt.setPromptCode("PROMPT_A");
        prompt.setModelName("qwen-plus");
        prompt.setProviderId("dashscope");
        prompt.setTemperature(0.3);
        prompt.setTopP(0.95);
        prompt.setEnableDeepThinking(Boolean.TRUE);

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(entity));
        when(promptsRepository.findByPromptCode("PROMPT_A")).thenReturn(Optional.of(prompt));

        SemanticTranslateConfigDTO dto = service.getAdminConfig();

        assertThat(dto.getModel()).isEqualTo("qwen-plus");
        assertThat(dto.getProviderId()).isEqualTo("dashscope");
        assertThat(dto.getTemperature()).isEqualTo(0.3);
        assertThat(dto.getTopP()).isEqualTo(0.95);
        assertThat(dto.getEnableThinking()).isTrue();
    }

    @Test
    void getAdminConfig_whenPromptCodeBlank_shouldSkipPromptLookupAndKeepPromptFieldsNull() {
        SemanticTranslateConfigEntity entity = new SemanticTranslateConfigEntity();
        entity.setEnabled(Boolean.TRUE);
        entity.setPromptCode("  ");
        entity.setAllowedTargetLangs("[\"zh-CN\"]");
        entity.setMaxContentChars(2000);
        entity.setUpdatedAt(LocalDateTime.now());

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(entity));

        SemanticTranslateConfigDTO dto = service.getAdminConfig();

        assertThat(dto.getModel()).isNull();
        assertThat(dto.getProviderId()).isNull();
        assertThat(dto.getTemperature()).isNull();
        assertThat(dto.getTopP()).isNull();
        assertThat(dto.getEnableThinking()).isNull();
        verify(promptsRepository, never()).findByPromptCode(anyString());
    }

    @Test
    void getPublicConfig_whenJsonValid_shouldParseAndNormalize() {
        SemanticTranslateConfigEntity entity = new SemanticTranslateConfigEntity();
        entity.setEnabled(Boolean.TRUE);
        entity.setAllowedTargetLangs("[\"zh-CN\",\"en\"]");

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(entity));

        SemanticTranslatePublicConfigDTO dto = service.getPublicConfig();

        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getAllowedTargetLanguages()).containsExactly("zh-CN", "en");
    }

    @Test
    void getPublicConfig_whenJsonInvalid_shouldFallbackToSplitLines() {
        SemanticTranslateConfigEntity entity = new SemanticTranslateConfigEntity();
        entity.setEnabled(Boolean.FALSE);
        entity.setAllowedTargetLangs(" zh-CN \r\nen\rfr ");

        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(entity));

        SemanticTranslatePublicConfigDTO dto = service.getPublicConfig();

        assertThat(dto.getEnabled()).isFalse();
        assertThat(dto.getAllowedTargetLanguages()).containsExactly("zh-CN", "en", "fr");
    }

    @Test
    void getPublicConfig_whenAllowedEmptyAndNoActiveCodes_shouldUseBuiltInDefaults() {
        SemanticTranslateConfigEntity entity = new SemanticTranslateConfigEntity();
        entity.setEnabled(Boolean.TRUE);
        entity.setAllowedTargetLangs("[]");

        when(supportedLanguageService.listActiveLanguageCodes()).thenReturn(List.of());
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(entity));

        SemanticTranslatePublicConfigDTO dto = service.getPublicConfig();

        assertThat(dto.getAllowedTargetLanguages()).containsExactly("zh-CN", "en");
    }

        @Test
        void getPublicConfig_whenConfigMissing_shouldUseDefaultEntityValues() {
                when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                                .thenReturn(Optional.empty());

                SemanticTranslatePublicConfigDTO dto = service.getPublicConfig();

                assertThat(dto.getEnabled()).isTrue();
                assertThat(dto.getAllowedTargetLanguages()).containsExactly("zh-CN", "en");
        }

        @Test
        void getPublicConfig_whenAllowedNullAndActiveCodesNull_shouldUseBuiltInDefaults() {
                SemanticTranslateConfigEntity entity = new SemanticTranslateConfigEntity();
                entity.setEnabled(Boolean.TRUE);
                entity.setAllowedTargetLangs(null);

                when(supportedLanguageService.listActiveLanguageCodes()).thenReturn(null);
                when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                                .thenReturn(Optional.of(entity));

                SemanticTranslatePublicConfigDTO dto = service.getPublicConfig();

                assertThat(dto.getAllowedTargetLanguages()).containsExactly("zh-CN", "en");
        }

    @Test
    void upsertAdminConfig_shouldThrow_whenPayloadNull() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existingConfig()));

        assertThatThrownBy(() -> service.upsertAdminConfig(null, 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload 不能为空");
    }

    @Test
    void upsertAdminConfig_shouldThrow_whenPromptCodeNull() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existingConfig()));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setPromptCode(null);

        assertThatThrownBy(() -> service.upsertAdminConfig(payload, 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("promptCode 不能为空");
    }

    @Test
    void upsertAdminConfig_shouldThrow_whenPromptCodeBlank() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existingConfig()));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setPromptCode("  ");

        assertThatThrownBy(() -> service.upsertAdminConfig(payload, 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("promptCode 不能为空");
    }

    @Test
    void upsertAdminConfig_shouldThrow_whenPromptCodeTooLong() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existingConfig()));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setPromptCode("x".repeat(65));

        assertThatThrownBy(() -> service.upsertAdminConfig(payload, 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度不能超过 64");
    }

    @Test
    void upsertAdminConfig_shouldThrow_whenMaxContentCharsTooSmall() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existingConfig()));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setMaxContentChars(199);

        assertThatThrownBy(() -> service.upsertAdminConfig(payload, 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxContentChars");
    }

    @Test
    void upsertAdminConfig_shouldThrow_whenMaxContentCharsTooLarge() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existingConfig()));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setMaxContentChars(100001);

        assertThatThrownBy(() -> service.upsertAdminConfig(payload, 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxContentChars");
    }



    @Test
    void upsertAdminConfig_shouldThrow_whenHistoryKeepDaysNotPositive() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existingConfig()));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setHistoryKeepDays(0);

        assertThatThrownBy(() -> service.upsertAdminConfig(payload, 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("historyKeepDays");
    }

    @Test
    void upsertAdminConfig_shouldThrow_whenHistoryKeepRowsNotPositive() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existingConfig()));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setHistoryKeepRows(0);

        assertThatThrownBy(() -> service.upsertAdminConfig(payload, 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("historyKeepRows");
    }

    @Test
    void upsertAdminConfig_shouldThrow_whenAllowedTargetLanguagesTooMany() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existingConfig()));

        SemanticTranslateConfigDTO payload = validPayload();
        List<String> languages = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            languages.add("l" + i);
        }
        payload.setAllowedTargetLanguages(languages);

        assertThatThrownBy(() -> service.upsertAdminConfig(payload, 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedTargetLanguages 过多");
    }

    @Test
    void upsertAdminConfig_shouldThrow_whenAllowedTargetLanguageItemTooLong() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existingConfig()));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setAllowedTargetLanguages(List.of("x".repeat(65)));

        assertThatThrownBy(() -> service.upsertAdminConfig(payload, 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("单项过长");
    }

    @Test
    void upsertAdminConfig_shouldThrow_whenPromptCodeNotFound() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existingConfig()));
        when(promptsRepository.findByPromptCode("PROMPT_MISSING")).thenReturn(Optional.empty());

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setPromptCode("PROMPT_MISSING");

        assertThatThrownBy(() -> service.upsertAdminConfig(payload, 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("promptCode 不存在");
    }

    @Test
    void upsertAdminConfig_shouldNormalizeAndPersistFields() {
        SemanticTranslateConfigEntity existing = existingConfig();
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existing));
        when(configRepository.save(any(SemanticTranslateConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, SemanticTranslateConfigEntity.class));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setEnabled(null);
        payload.setEnableThinking(null);
        payload.setHistoryEnabled(null);
        payload.setModel("   ");
        payload.setProviderId("  provider-x  ");
        payload.setMaxContentChars(null);
        payload.setAllowedTargetLanguages(Arrays.asList(" zh-CN ", "", "en", "en", null));

        SemanticTranslateConfigDTO dto = service.upsertAdminConfig(payload, 9L, "operator");

        ArgumentCaptor<SemanticTranslateConfigEntity> captor = ArgumentCaptor.forClass(SemanticTranslateConfigEntity.class);
        verify(configRepository).save(captor.capture());
        SemanticTranslateConfigEntity saved = captor.getValue();

        assertThat(saved.getEnabled()).isFalse();
        assertThat(saved.getHistoryEnabled()).isFalse();
        assertThat(saved.getMaxContentChars()).isEqualTo(SemanticTranslateConfigService.DEFAULT_MAX_CONTENT_CHARS);
        assertThat(saved.getUpdatedBy()).isEqualTo(9L);
        assertThat(saved.getUpdatedAt()).isNotNull();

        assertThat(dto.getUpdatedBy()).isEqualTo("operator");
        assertThat(dto.getAllowedTargetLanguages()).containsExactly("zh-CN", "en");
    }

    @Test
    void upsertAdminConfig_shouldTrimNonBlankModelAndProvider() {
        SemanticTranslateConfigEntity existing = existingConfig();
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existing));
        when(configRepository.save(any(SemanticTranslateConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, SemanticTranslateConfigEntity.class));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setModel("  model-x  ");
        payload.setProviderId("  provider-y  ");

        service.upsertAdminConfig(payload, 5L, "admin");

        ArgumentCaptor<SemanticTranslateConfigEntity> captor = ArgumentCaptor.forClass(SemanticTranslateConfigEntity.class);
        verify(configRepository).save(captor.capture());
        SemanticTranslateConfigEntity saved = captor.getValue();
    }

    @Test
    void upsertAdminConfig_shouldKeepTemperatureAndTopPNullWhenNotProvided() {
        SemanticTranslateConfigEntity existing = existingConfig();
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existing));
        when(configRepository.save(any(SemanticTranslateConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, SemanticTranslateConfigEntity.class));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setTemperature(null);
        payload.setTopP(null);

        service.upsertAdminConfig(payload, 5L, "admin");

        ArgumentCaptor<SemanticTranslateConfigEntity> captor = ArgumentCaptor.forClass(SemanticTranslateConfigEntity.class);
        verify(configRepository).save(captor.capture());
        SemanticTranslateConfigEntity saved = captor.getValue();
    }

    @Test
    void upsertAdminConfig_whenAllowedLanguagesEmpty_shouldUseDefaultActiveCodes() {
        when(supportedLanguageService.listActiveLanguageCodes()).thenReturn(List.of("fr", "en"));

        SemanticTranslateConfigEntity existing = existingConfig();
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existing));
        when(configRepository.save(any(SemanticTranslateConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, SemanticTranslateConfigEntity.class));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setAllowedTargetLanguages(Arrays.asList(" ", "\n", null));

        SemanticTranslateConfigDTO dto = service.upsertAdminConfig(payload, 1L, "admin");

        assertThat(dto.getAllowedTargetLanguages()).containsExactly("fr", "en");
    }

    @Test
    void upsertAdminConfig_whenAllowedLanguagesEmptyAndNoActiveCodes_shouldFallbackToBuiltInDefaults() {
        when(supportedLanguageService.listActiveLanguageCodes()).thenReturn(null);

        SemanticTranslateConfigEntity existing = existingConfig();
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existing));
        when(configRepository.save(any(SemanticTranslateConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, SemanticTranslateConfigEntity.class));

        SemanticTranslateConfigDTO payload = validPayload();
        payload.setAllowedTargetLanguages(List.of(" ", "\n"));

        SemanticTranslateConfigDTO dto = service.upsertAdminConfig(payload, 1L, "admin");

        assertThat(dto.getAllowedTargetLanguages()).containsExactly("zh-CN", "en");
    }

    @Test
    void upsertAdminConfig_whenNoExistingConfig_shouldCreateFromDefaultEntity() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.empty());
        when(configRepository.save(any(SemanticTranslateConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, SemanticTranslateConfigEntity.class));

        SemanticTranslateConfigDTO payload = validPayload();
        SemanticTranslateConfigDTO dto = service.upsertAdminConfig(payload, 77L, "root");

        assertThat(dto.getPromptCode()).isEqualTo(payload.getPromptCode());
        assertThat(dto.getUpdatedBy()).isEqualTo("root");
        verify(configRepository).save(any(SemanticTranslateConfigEntity.class));
    }

    @Test
        void upsertAdminConfig_shouldThrow_whenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(existingConfig()));
        when(supportedLanguageService.listActiveLanguageCodes()).thenReturn(List.of("zh-CN", "en"));
        when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));

        SemanticTranslateConfigService localService = new SemanticTranslateConfigService(
                configRepository,
                historyRepository,
                promptsRepository,
                failingMapper,
                supportedLanguageService
        );

        assertThatThrownBy(() -> localService.upsertAdminConfig(validPayload(), 1L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("序列化失败")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void listHistory_whenUserIdNull_shouldUseFindAllAndClampPageSize() {
        SemanticTranslateHistoryEntity row = historyEntity(1L, 11L);
        when(historyRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        Page<SemanticTranslateHistoryDTO> page = service.listHistory(null, -3, 2000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(historyRepository).findAllByOrderByCreatedAtDesc(pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(100);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(page.getContent().get(0).getUserId()).isEqualTo(11L);
    }

    @Test
    void listHistory_whenUserIdPresent_shouldUseUserQueryAndClampSizeLowerBound() {
        SemanticTranslateHistoryEntity row = historyEntity(2L, 22L);
        when(historyRepository.findByUserIdOrderByCreatedAtDesc(eq(22L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        Page<SemanticTranslateHistoryDTO> page = service.listHistory(22L, 5, 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(historyRepository).findByUserIdOrderByCreatedAtDesc(eq(22L), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(5);
        assertThat(pageable.getPageSize()).isEqualTo(1);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(2L);
                assertThat(page.getContent().get(0).getSourceType()).isEqualTo("POST");
                assertThat(page.getContent().get(0).getSourceId()).isEqualTo(200L);
                assertThat(page.getContent().get(0).getTargetLang()).isEqualTo("en");
                assertThat(page.getContent().get(0).getSourceTitleExcerpt()).isEqualTo("title");
                assertThat(page.getContent().get(0).getSourceContentExcerpt()).isEqualTo("content");
                assertThat(page.getContent().get(0).getTranslatedTitle()).isEqualTo("translated title");
                assertThat(page.getContent().get(0).getTranslatedMarkdown()).isEqualTo("translated markdown");
    }

    @Test
    void recordHistory_whenNull_shouldNotSave() {
        service.recordHistory(null);

        verify(historyRepository, never()).save(any(SemanticTranslateHistoryEntity.class));
    }

    @Test
    void recordHistory_whenNonNull_shouldSave() {
        SemanticTranslateHistoryEntity row = historyEntity(3L, 33L);

        service.recordHistory(row);

        verify(historyRepository).save(row);
    }

    @Test
    void getConfigEntityOrDefault_shouldReturnRepoValueWhenPresent() {
        SemanticTranslateConfigEntity entity = existingConfig();
        entity.setPromptCode("REPO_PROMPT");
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.of(entity));

        SemanticTranslateConfigEntity out = service.getConfigEntityOrDefault();

        assertThat(out.getPromptCode()).isEqualTo("REPO_PROMPT");
    }

    @Test
    void getConfigEntityOrDefault_shouldReturnDefaultWhenRepoMissing() {
        when(configRepository.findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc("SEMANTIC_TRANSLATE", "DEFAULT"))
                .thenReturn(Optional.empty());

        SemanticTranslateConfigEntity out = service.getConfigEntityOrDefault();

        assertThat(out.getPromptCode()).isEqualTo(SemanticTranslateConfigService.DEFAULT_PROMPT_CODE);
        assertThat(out.getAllowedTargetLangs()).isNotBlank();
    }

    private SemanticTranslateConfigDTO validPayload() {
        SemanticTranslateConfigDTO payload = new SemanticTranslateConfigDTO();
        payload.setEnabled(Boolean.TRUE);
        payload.setPromptCode("PROMPT_OK");
        payload.setModel("gpt-4o-mini");
        payload.setProviderId("provider-a");
        payload.setTemperature(0.5);
        payload.setTopP(0.8);
        payload.setEnableThinking(Boolean.TRUE);
        payload.setMaxContentChars(1200);
        payload.setHistoryEnabled(Boolean.TRUE);
        payload.setHistoryKeepDays(7);
        payload.setHistoryKeepRows(300);
        payload.setAllowedTargetLanguages(List.of("zh-CN", "en"));
        return payload;
    }

    private SemanticTranslateConfigEntity existingConfig() {
        SemanticTranslateConfigEntity entity = new SemanticTranslateConfigEntity();
        entity.setId(100L);
        entity.setGroupCode("SEMANTIC_TRANSLATE");
        entity.setSubType("DEFAULT");
        entity.setEnabled(Boolean.TRUE);
        entity.setPromptCode("OLD_PROMPT");
        entity.setMaxContentChars(8000);
        entity.setHistoryEnabled(Boolean.TRUE);
        entity.setHistoryKeepDays(30);
        entity.setHistoryKeepRows(5000);
        entity.setAllowedTargetLangs("[\"zh-CN\",\"en\"]");
        entity.setVersion(1);
        entity.setUpdatedAt(LocalDateTime.now().minusDays(1));
        entity.setUpdatedBy(1L);
        return entity;
    }

    private SemanticTranslateHistoryEntity historyEntity(Long id, Long userId) {
        SemanticTranslateHistoryEntity entity = new SemanticTranslateHistoryEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setSourceType("POST");
        entity.setSourceId(200L);
        entity.setTargetLang("en");
        entity.setSourceHash("source-hash");
        entity.setConfigHash("config-hash");
        entity.setSourceTitleExcerpt("title");
        entity.setSourceContentExcerpt("content");
        entity.setTranslatedTitle("translated title");
        entity.setTranslatedMarkdown("translated markdown");
        return entity;
    }
}
