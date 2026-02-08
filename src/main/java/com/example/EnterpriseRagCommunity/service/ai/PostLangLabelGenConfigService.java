package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostLangLabelGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostLangLabelGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostLangLabelGenConfigEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostLangLabelGenConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PostLangLabelGenConfigService {

    public static final int DEFAULT_MAX_CONTENT_CHARS = 8000;
    private static final String GROUP_CODE = "POST_LANG_LABEL";
    private static final String SUB_TYPE = "DEFAULT";

    public static final String DEFAULT_SYSTEM_PROMPT = """
你是一个语言识别助手。
任务：根据输入的标题与正文，判断文本包含的自然语言。
输出要求：
1. 只输出 JSON（不要包裹 ```），格式：{\"languages\":[\"zh\",\"en\"]}
2. languages 使用简短语言代码（优先 ISO 639-1：zh/en/ja/ko/fr/de/es/ru/it/pt/...）。中文统一用 zh。
3. 如果文本明显由多种语言混合组成，请输出多个语言代码（最多 3 个）。
4. 不要输出解释、不要输出多余字段。
""";

    public static final String DEFAULT_PROMPT_TEMPLATE = """
标题：
{{title}}

正文：
{{content}}
""";

    private final PostLangLabelGenConfigRepository configRepository;

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
        e.setSystemPrompt(DEFAULT_SYSTEM_PROMPT);
        e.setPromptTemplate(DEFAULT_PROMPT_TEMPLATE);
        e.setModel(null);
        e.setProviderId(null);
        e.setTemperature(0.0);
        e.setMaxContentChars(DEFAULT_MAX_CONTENT_CHARS);
        e.setVersion(0);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(null);
        return e;
    }

    private PostLangLabelGenConfigEntity mergeAndValidate(PostLangLabelGenConfigEntity base, PostLangLabelGenConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");

        String systemPrompt = payload.getSystemPrompt() == null ? "" : payload.getSystemPrompt().trim();
        String promptTemplate = payload.getPromptTemplate() == null ? "" : payload.getPromptTemplate().trim();
        if (systemPrompt.isBlank()) throw new IllegalArgumentException("systemPrompt 不能为空");
        if (promptTemplate.isBlank()) throw new IllegalArgumentException("promptTemplate 不能为空");
        if (promptTemplate.length() > 20000) throw new IllegalArgumentException("promptTemplate 过长（>20000），请精简");

        Integer maxContentChars = payload.getMaxContentChars();
        if (maxContentChars == null) maxContentChars = DEFAULT_MAX_CONTENT_CHARS;
        if (maxContentChars < 200 || maxContentChars > 100000) throw new IllegalArgumentException("maxContentChars 需在 [200,100000] 范围内");

        Double temperature = payload.getTemperature();
        if (temperature != null && (temperature < 0 || temperature > 2)) {
            throw new IllegalArgumentException("temperature 需在 [0,2] 范围内");
        }

        base.setEnabled(Boolean.TRUE.equals(payload.getEnabled()));
        base.setSystemPrompt(systemPrompt);
        base.setPromptTemplate(promptTemplate);

        String model = payload.getModel();
        base.setModel(model == null || model.isBlank() ? null : model.trim());
        String providerId = payload.getProviderId();
        base.setProviderId(providerId == null || providerId.isBlank() ? null : providerId.trim());
        base.setTemperature(temperature);
        base.setMaxContentChars(maxContentChars);
        return base;
    }

    private PostLangLabelGenConfigDTO toDto(PostLangLabelGenConfigEntity e, String updatedByName) {
        PostLangLabelGenConfigDTO dto = new PostLangLabelGenConfigDTO();
        dto.setId(e.getId());
        dto.setVersion(e.getVersion());
        dto.setEnabled(e.getEnabled());
        dto.setSystemPrompt(e.getSystemPrompt());
        dto.setPromptTemplate(e.getPromptTemplate());
        dto.setModel(e.getModel());
        dto.setProviderId(e.getProviderId());
        dto.setTemperature(e.getTemperature());
        dto.setMaxContentChars(e.getMaxContentChars());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }
}
