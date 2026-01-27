package com.example.EnterpriseRagCommunity.service.ai;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.ai.PostRiskTagGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostRiskTagGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostRiskTagGenConfigEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostRiskTagGenConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostRiskTagGenConfigService {

    public static final int DEFAULT_MAX_CONTENT_CHARS = 8000;
    public static final int DEFAULT_MAX_COUNT = 10;

    public static final String DEFAULT_SYSTEM_PROMPT = """
你是一个社区内容风险识别助手。
任务：根据输入的标题与正文，生成该帖子可能涉及的风险标签。
输出要求：
1. 只输出 JSON（不要包裹 ```），格式：{"riskTags":["诈骗","隐私泄露","仇恨言论"]}。
2. riskTags 必须使用中文短语（不要英文/拼音），每个标签不超过 8 个汉字。
3. 标签应尽量稳定、可复用、能概括风险类型；最多输出 {{maxCount}} 个。
4. 如果内容看起来风险很低，可以输出空数组。
5. 不要输出解释、不要输出多余字段。
""";

    public static final String DEFAULT_PROMPT_TEMPLATE = """
标题：
{{title}}

正文：
{{content}}
""";

    private final PostRiskTagGenConfigRepository configRepository;

    @Transactional(readOnly = true)
    public PostRiskTagGenConfigDTO getAdminConfig() {
        PostRiskTagGenConfigEntity cfg = configRepository.findTopByOrderByUpdatedAtDesc().orElse(null);
        if (cfg == null) return toDto(defaultEntity(), null);
        return toDto(cfg, null);
    }

    @Transactional(readOnly = true)
    public PostRiskTagGenPublicConfigDTO getPublicConfig() {
        PostRiskTagGenConfigEntity cfg = configRepository.findTopByOrderByUpdatedAtDesc().orElse(null);
        if (cfg == null) cfg = defaultEntity();
        PostRiskTagGenPublicConfigDTO dto = new PostRiskTagGenPublicConfigDTO();
        dto.setEnabled(Boolean.TRUE.equals(cfg.getEnabled()));
        dto.setMaxCount(cfg.getMaxCount());
        dto.setMaxContentChars(cfg.getMaxContentChars());
        return dto;
    }

    @Transactional
    public PostRiskTagGenConfigDTO upsertAdminConfig(PostRiskTagGenConfigDTO payload, Long actorUserId, String actorUsername) {
        PostRiskTagGenConfigEntity cfg = configRepository.findAll().stream().findFirst().orElseGet(this::defaultEntity);

        PostRiskTagGenConfigEntity merged = mergeAndValidate(cfg, payload);
        merged.setUpdatedAt(LocalDateTime.now());
        merged.setUpdatedBy(actorUserId);

        merged = configRepository.save(merged);
        return toDto(merged, actorUsername);
    }

    @Transactional(readOnly = true)
    public PostRiskTagGenConfigEntity getConfigEntityOrDefault() {
        return configRepository.findTopByOrderByUpdatedAtDesc().orElseGet(this::defaultEntity);
    }

    private PostRiskTagGenConfigEntity defaultEntity() {
        PostRiskTagGenConfigEntity e = new PostRiskTagGenConfigEntity();
        e.setEnabled(Boolean.TRUE);
        e.setSystemPrompt(DEFAULT_SYSTEM_PROMPT);
        e.setPromptTemplate(DEFAULT_PROMPT_TEMPLATE);
        e.setModel(null);
        e.setTemperature(0.2);
        e.setMaxCount(DEFAULT_MAX_COUNT);
        e.setMaxContentChars(DEFAULT_MAX_CONTENT_CHARS);
        e.setVersion(0);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(null);
        return e;
    }

    private PostRiskTagGenConfigEntity mergeAndValidate(PostRiskTagGenConfigEntity base, PostRiskTagGenConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");

        String systemPrompt = payload.getSystemPrompt() == null ? "" : payload.getSystemPrompt().trim();
        String promptTemplate = payload.getPromptTemplate() == null ? "" : payload.getPromptTemplate().trim();
        if (systemPrompt.isBlank()) throw new IllegalArgumentException("systemPrompt 不能为空");
        if (promptTemplate.isBlank()) throw new IllegalArgumentException("promptTemplate 不能为空");
        if (promptTemplate.length() > 20000) throw new IllegalArgumentException("promptTemplate 过长（>20000），请精简");

        Integer maxContentChars = payload.getMaxContentChars();
        if (maxContentChars == null) maxContentChars = DEFAULT_MAX_CONTENT_CHARS;
        if (maxContentChars < 200 || maxContentChars > 100000) throw new IllegalArgumentException("maxContentChars 需在 [200,100000] 范围内");

        Integer maxCount = payload.getMaxCount();
        if (maxCount == null) maxCount = DEFAULT_MAX_COUNT;
        if (maxCount < 1 || maxCount > 50) throw new IllegalArgumentException("maxCount 需在 [1,50] 范围内");

        Double temperature = payload.getTemperature();
        if (temperature != null && (temperature < 0 || temperature > 2)) {
            throw new IllegalArgumentException("temperature 需在 [0,2] 范围内");
        }

        base.setEnabled(Boolean.TRUE.equals(payload.getEnabled()));
        base.setSystemPrompt(systemPrompt);
        base.setPromptTemplate(promptTemplate);

        String model = payload.getModel();
        base.setModel(model == null || model.isBlank() ? null : model.trim());
        base.setTemperature(temperature);
        base.setMaxCount(maxCount);
        base.setMaxContentChars(maxContentChars);
        return base;
    }

    private PostRiskTagGenConfigDTO toDto(PostRiskTagGenConfigEntity e, String updatedByName) {
        PostRiskTagGenConfigDTO dto = new PostRiskTagGenConfigDTO();
        dto.setId(e.getId());
        dto.setVersion(e.getVersion());
        dto.setEnabled(e.getEnabled());
        dto.setSystemPrompt(e.getSystemPrompt());
        dto.setPromptTemplate(e.getPromptTemplate());
        dto.setModel(e.getModel());
        dto.setTemperature(e.getTemperature());
        dto.setMaxCount(e.getMaxCount());
        dto.setMaxContentChars(e.getMaxContentChars());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }
}
