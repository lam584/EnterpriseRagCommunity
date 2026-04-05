package com.example.EnterpriseRagCommunity.service.ai;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.ai.PostLangLabelGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostLangLabelGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostLangLabelGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostLangLabelGenConfigRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostLangLabelGenConfigService {

    public static final int DEFAULT_MAX_CONTENT_CHARS = 8000;
    private static final String GROUP_CODE = "POST_LANG_LABEL";
    private static final String SUB_TYPE = "DEFAULT";
    public static final String DEFAULT_PROMPT_CODE = "LANG_DETECT";

    private final PostLangLabelGenConfigRepository configRepository;
    private final PromptsRepository promptsRepository;

    @Transactional(readOnly = true)
    public PostLangLabelGenConfigDTO getAdminConfig() {
        PostLangLabelGenConfigEntity cfg = configRepository
                .findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(GROUP_CODE, SUB_TYPE)
                .orElse(null);
        if (cfg == null) return toDto(defaultEntity(), null);
        return toDto(cfg, null);
    }

    @Transactional(readOnly = true)
    public PostLangLabelGenPublicConfigDTO getPublicConfig() {
        PostLangLabelGenConfigEntity cfg = configRepository
                .findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(GROUP_CODE, SUB_TYPE)
                .orElse(null);
        if (cfg == null) cfg = defaultEntity();
        PostLangLabelGenPublicConfigDTO dto = new PostLangLabelGenPublicConfigDTO();
        dto.setEnabled(Boolean.TRUE.equals(cfg.getEnabled()));
        dto.setMaxContentChars(cfg.getMaxContentChars());
        return dto;
    }

    @Transactional
    public PostLangLabelGenConfigDTO upsertAdminConfig(PostLangLabelGenConfigDTO payload, Long actorUserId, String actorUsername) {
        PostLangLabelGenConfigEntity cfg = configRepository
                .findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(GROUP_CODE, SUB_TYPE)
                .orElseGet(this::defaultEntity);

        PostLangLabelGenConfigEntity merged = mergeAndValidate(cfg, payload);
        merged.setUpdatedAt(LocalDateTime.now());
        merged.setUpdatedBy(actorUserId);

        merged = configRepository.save(merged);
        return toDto(merged, actorUsername);
    }

    @Transactional(readOnly = true)
    public PostLangLabelGenConfigEntity getConfigEntityOrDefault() {
        return configRepository
                .findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(GROUP_CODE, SUB_TYPE)
                .orElseGet(this::defaultEntity);
    }

    private PostLangLabelGenConfigEntity defaultEntity() {
        PostLangLabelGenConfigEntity e = new PostLangLabelGenConfigEntity();
        e.setGroupCode(GROUP_CODE);
        e.setSubType(SUB_TYPE);
        e.setEnabled(Boolean.TRUE);
        e.setPromptCode(DEFAULT_PROMPT_CODE);
        e.setMaxContentChars(DEFAULT_MAX_CONTENT_CHARS);
        e.setVersion(0);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(null);
        return e;
    }

    private PostLangLabelGenConfigEntity mergeAndValidate(PostLangLabelGenConfigEntity base, PostLangLabelGenConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");
        PromptConfigValidationSupport.ValidatedPromptConfig validated =
                PromptConfigValidationSupport.validatePromptCodeAndMaxContentChars(
                        payload.getPromptCode(),
                        payload.getMaxContentChars(),
                        DEFAULT_MAX_CONTENT_CHARS,
                        100000
                );

        base.setEnabled(Boolean.TRUE.equals(payload.getEnabled()));
        base.setPromptCode(validated.promptCode());
        base.setMaxContentChars(validated.maxContentChars());

        promptsRepository.findByPromptCode(validated.promptCode())
            .orElseThrow(() -> new IllegalArgumentException("promptCode 不存在: " + validated.promptCode()));
        return base;
    }

    private PostLangLabelGenConfigDTO toDto(PostLangLabelGenConfigEntity e, String updatedByName) {
        PostLangLabelGenConfigDTO dto = new PostLangLabelGenConfigDTO();
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
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }
}
