package com.example.EnterpriseRagCommunity.service.ai;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslateHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslatePublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.SemanticTranslateConfigRepository;
import com.example.EnterpriseRagCommunity.repository.ai.SemanticTranslateHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SemanticTranslateConfigService {

    public static final int DEFAULT_MAX_CONTENT_CHARS = 8000;
    private static final String GROUP_CODE = "SEMANTIC_TRANSLATE";
    private static final String SUB_TYPE = "DEFAULT";
    public static final String DEFAULT_PROMPT_CODE = "TRANSLATE_GEN";

    private final SemanticTranslateConfigRepository configRepository;
    private final SemanticTranslateHistoryRepository historyRepository;
    private final PromptsRepository promptsRepository;
    private final ObjectMapper objectMapper;
    private final SupportedLanguageService supportedLanguageService;

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private List<String> defaultAllowedTargetLanguageCodes() {
        List<String> codes = supportedLanguageService.listActiveLanguageCodes();
        if (codes == null || codes.isEmpty()) {
            return List.of(SupportedLanguageService.DEFAULT_LANGUAGE_CODE, "en");
        }
        return codes;
    }

    @Transactional(readOnly = true)
    public SemanticTranslateConfigDTO getAdminConfig() {
        SemanticTranslateConfigEntity cfg = configRepository
                .findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(GROUP_CODE, SUB_TYPE)
                .orElse(null);
        if (cfg == null) return toDto(defaultEntity(), null);
        return toDto(cfg, null);
    }

    @Transactional(readOnly = true)
    public SemanticTranslatePublicConfigDTO getPublicConfig() {
        SemanticTranslateConfigEntity cfg = configRepository
                .findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(GROUP_CODE, SUB_TYPE)
                .orElse(null);
        if (cfg == null) cfg = defaultEntity();

        SemanticTranslatePublicConfigDTO dto = new SemanticTranslatePublicConfigDTO();
        dto.setEnabled(Boolean.TRUE.equals(cfg.getEnabled()));
        List<String> allowed = parseAllowedTargetLanguages(cfg.getAllowedTargetLangs());
        if (allowed == null || allowed.isEmpty()) allowed = defaultAllowedTargetLanguageCodes();
        dto.setAllowedTargetLanguages(allowed);
        return dto;
    }

    @Transactional
    public SemanticTranslateConfigDTO upsertAdminConfig(SemanticTranslateConfigDTO payload, Long actorUserId, String actorUsername) {
        SemanticTranslateConfigEntity cfg = configRepository
                .findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(GROUP_CODE, SUB_TYPE)
                .orElseGet(this::defaultEntity);

        SemanticTranslateConfigEntity merged = mergeAndValidate(cfg, payload);
        merged.setUpdatedAt(LocalDateTime.now());
        merged.setUpdatedBy(actorUserId);

        merged = configRepository.save(merged);
        return toDto(merged, actorUsername);
    }

    @Transactional(readOnly = true)
    public Page<SemanticTranslateHistoryDTO> listHistory(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<SemanticTranslateHistoryEntity> rows = (userId == null)
                ? historyRepository.findAllByOrderByCreatedAtDesc(pageable)
                : historyRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return rows.map(this::toHistoryDto);
    }

    @Transactional
    public void recordHistory(SemanticTranslateHistoryEntity e) {
        if (e == null) return;
        historyRepository.save(e);
    }

    @Transactional(readOnly = true)
    public SemanticTranslateConfigEntity getConfigEntityOrDefault() {
        return configRepository
                .findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(GROUP_CODE, SUB_TYPE)
                .orElseGet(this::defaultEntity);
    }

    private SemanticTranslateConfigEntity defaultEntity() {
        SemanticTranslateConfigEntity e = new SemanticTranslateConfigEntity();
        e.setGroupCode(GROUP_CODE);
        e.setSubType(SUB_TYPE);
        e.setEnabled(Boolean.TRUE);
        e.setPromptCode(DEFAULT_PROMPT_CODE);
        e.setMaxContentChars(DEFAULT_MAX_CONTENT_CHARS);
        e.setHistoryEnabled(Boolean.TRUE);
        e.setHistoryKeepDays(30);
        e.setHistoryKeepRows(5000);
        e.setAllowedTargetLangs(serializeAllowedTargetLanguages(defaultAllowedTargetLanguageCodes()));
        e.setVersion(0);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(null);
        return e;
    }

    private SemanticTranslateConfigEntity mergeAndValidate(SemanticTranslateConfigEntity base, SemanticTranslateConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");
        PromptConfigValidationSupport.ValidatedPromptConfig validated =
                PromptConfigValidationSupport.validatePromptCodeAndMaxContentChars(
                        payload.getPromptCode(),
                        payload.getMaxContentChars(),
                        DEFAULT_MAX_CONTENT_CHARS,
                        100000
                );

        Integer historyKeepDays = payload.getHistoryKeepDays();
        if (historyKeepDays != null && historyKeepDays < 1) throw new IllegalArgumentException("historyKeepDays 必须为正数");
        Integer historyKeepRows = payload.getHistoryKeepRows();
        if (historyKeepRows != null && historyKeepRows < 1) throw new IllegalArgumentException("historyKeepRows 必须为正数");

        List<String> allowedTargetLanguages = normalizeStringList(payload.getAllowedTargetLanguages());
        if (allowedTargetLanguages.size() > 500) throw new IllegalArgumentException("allowedTargetLanguages 过多（>500），请精简");
        for (String s : allowedTargetLanguages) {
            if (s != null && s.length() > 64) {
                throw new IllegalArgumentException("allowedTargetLanguages 单项过长（>64），请精简");
            }
        }

        base.setEnabled(Boolean.TRUE.equals(payload.getEnabled()));
        base.setPromptCode(validated.promptCode());
        base.setMaxContentChars(validated.maxContentChars());

        promptsRepository.findByPromptCode(validated.promptCode())
            .orElseThrow(() -> new IllegalArgumentException("promptCode 不存在: " + validated.promptCode()));

        base.setHistoryEnabled(Boolean.TRUE.equals(payload.getHistoryEnabled()));
        base.setHistoryKeepDays(historyKeepDays);
        base.setHistoryKeepRows(historyKeepRows);
        base.setAllowedTargetLangs(
                serializeAllowedTargetLanguages(allowedTargetLanguages.isEmpty() ? defaultAllowedTargetLanguageCodes() : allowedTargetLanguages)
        );
        return base;
    }

    private SemanticTranslateConfigDTO toDto(SemanticTranslateConfigEntity e, String updatedByName) {
        SemanticTranslateConfigDTO dto = new SemanticTranslateConfigDTO();
        dto.setId(e.getId());
        dto.setVersion(e.getVersion());
        dto.setEnabled(e.getEnabled());
        dto.setPromptCode(e.getPromptCode());
        PromptsEntity prompt = (e.getPromptCode() == null || e.getPromptCode().isBlank())
            ? null
            : promptsRepository.findByPromptCode(e.getPromptCode()).orElse(null);
        dto.setModel(prompt != null ? prompt.getModelName() : null);
        dto.setProviderId(prompt != null ? prompt.getProviderId() : null);
        dto.setTemperature(prompt != null ? prompt.getTemperature() : null);
        dto.setTopP(prompt != null ? prompt.getTopP() : null);
        dto.setEnableThinking(prompt != null ? prompt.getEnableDeepThinking() : null);
        dto.setMaxContentChars(e.getMaxContentChars());
        dto.setHistoryEnabled(e.getHistoryEnabled());
        dto.setHistoryKeepDays(e.getHistoryKeepDays());
        dto.setHistoryKeepRows(e.getHistoryKeepRows());
        List<String> allowed = parseAllowedTargetLanguages(e.getAllowedTargetLangs());
        dto.setAllowedTargetLanguages((allowed == null || allowed.isEmpty()) ? defaultAllowedTargetLanguageCodes() : allowed);
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }

    private SemanticTranslateHistoryDTO toHistoryDto(SemanticTranslateHistoryEntity e) {
        SemanticTranslateHistoryDTO dto = new SemanticTranslateHistoryDTO();
        dto.setId(e.getId());
        dto.setUserId(e.getUserId());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setSourceType(e.getSourceType());
        dto.setSourceId(e.getSourceId());
        dto.setTargetLang(e.getTargetLang());
        dto.setSourceTitleExcerpt(e.getSourceTitleExcerpt());
        dto.setSourceContentExcerpt(e.getSourceContentExcerpt());
        dto.setTranslatedTitle(e.getTranslatedTitle());
        dto.setTranslatedMarkdown(e.getTranslatedMarkdown());
        return dto;
    }

    private List<String> parseAllowedTargetLanguages(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            List<String> list = objectMapper.readValue(json, STRING_LIST_TYPE);
            return normalizeStringList(list).stream().map(supportedLanguageService::normalizeToLanguageCode).toList();
        } catch (Exception ignore) {
            return normalizeStringList(splitLines(json)).stream().map(supportedLanguageService::normalizeToLanguageCode).toList();
        }
    }

    private String serializeAllowedTargetLanguages(List<String> list) {
        List<String> normalized = normalizeStringList(list);
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new IllegalArgumentException("allowedTargetLanguages 序列化失败", e);
        }
    }

    private static List<String> splitLines(String text) {
        if (text == null) return List.of();
        String t = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] parts = t.split("\n");
        List<String> out = new ArrayList<>(parts.length);
        out.addAll(Arrays.asList(parts));
        return out;
    }

    private static List<String> normalizeStringList(List<String> list) {
        if (list == null || list.isEmpty()) return List.of();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String raw : list) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isBlank()) continue;
            set.add(s);
        }
        return new ArrayList<>(set);
    }
}
