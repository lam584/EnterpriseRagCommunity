package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SuggestionKind;
import com.example.EnterpriseRagCommunity.repository.ai.PostSuggestionGenConfigRepository;
import com.example.EnterpriseRagCommunity.repository.ai.PostSuggestionGenHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PostTagGenConfigService {

    public static final int DEFAULT_DEFAULT_COUNT = 5;
    public static final int DEFAULT_MAX_COUNT = 10;
    public static final int DEFAULT_MAX_CONTENT_CHARS = 4000;

    public static final String DEFAULT_SYSTEM_PROMPT = "你是专业的中文社区运营编辑，擅长为帖子提炼主题标签。";
    public static final String DEFAULT_PROMPT_TEMPLATE = """
请根据下面这段帖子内容生成 {{count}} 个中文主题标签。
要求：
- 标签应概括内容主题，优先使用常见领域词汇
- 每个标签不超过 8 个汉字
- 标签之间不要重复
- 不要输出编号、不要输出解释文字
- 只输出严格 JSON
- JSON 格式：{"tags":["...","..."]}

{{boardLine}}{{titleLine}}{{tagsLine}}帖子内容：
{{content}}
""";

    private static final SuggestionKind KIND = SuggestionKind.TOPIC_TAG;
    private static final String GROUP_CODE = "POST_SUGGESTION";

    private final PostSuggestionGenConfigRepository configRepository;
    private final PostSuggestionGenHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public PostTagGenConfigDTO getAdminConfig() {
        PostSuggestionGenConfigEntity cfg = configRepository
                .findTopByGroupCodeAndKindOrderByUpdatedAtDesc(GROUP_CODE, KIND)
                .orElse(null);
        if (cfg == null) return toDto(defaultEntity(), null);
        return toDto(cfg, null);
    }

    @Transactional(readOnly = true)
    public PostTagGenPublicConfigDTO getPublicConfig() {
        PostSuggestionGenConfigEntity cfg = configRepository
                .findTopByGroupCodeAndKindOrderByUpdatedAtDesc(GROUP_CODE, KIND)
                .orElse(null);
        if (cfg == null) cfg = defaultEntity();

        PostTagGenPublicConfigDTO dto = new PostTagGenPublicConfigDTO();
        dto.setEnabled(Boolean.TRUE.equals(cfg.getEnabled()));
        dto.setDefaultCount(cfg.getDefaultCount());
        dto.setMaxCount(cfg.getMaxCount());
        dto.setMaxContentChars(cfg.getMaxContentChars());
        return dto;
    }

    @Transactional
    public PostTagGenConfigDTO upsertAdminConfig(PostTagGenConfigDTO payload, Long actorUserId, String actorUsername) {
        PostSuggestionGenConfigEntity cfg = configRepository
                .findTopByGroupCodeAndKindOrderByUpdatedAtDesc(GROUP_CODE, KIND)
                .orElseGet(this::defaultEntity);

        PostSuggestionGenConfigEntity merged = mergeAndValidate(cfg, payload);
        merged.setUpdatedAt(LocalDateTime.now());
        merged.setUpdatedBy(actorUserId);

        merged = configRepository.save(merged);
        return toDto(merged, actorUsername);
    }

    @Transactional(readOnly = true)
    public Page<PostTagGenHistoryDTO> listHistory(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<PostSuggestionGenHistoryEntity> rows = (userId == null)
                ? historyRepository.findByKindOrderByCreatedAtDesc(KIND, pageable)
                : historyRepository.findByKindAndUserIdOrderByCreatedAtDesc(KIND, userId, pageable);

        return rows.map(this::toHistoryDto);
    }

    @Transactional
    public void recordHistory(PostSuggestionGenHistoryEntity e) {
        if (e == null) return;
        historyRepository.save(e);
    }

    public PostSuggestionGenConfigEntity getConfigEntityOrDefault() {
        return configRepository
                .findTopByGroupCodeAndKindOrderByUpdatedAtDesc(GROUP_CODE, KIND)
                .orElseGet(this::defaultEntity);
    }

    private PostSuggestionGenConfigEntity defaultEntity() {
        PostSuggestionGenConfigEntity e = new PostSuggestionGenConfigEntity();
        e.setGroupCode(GROUP_CODE);
        e.setKind(KIND);
        e.setEnabled(Boolean.TRUE);
        e.setSystemPrompt(DEFAULT_SYSTEM_PROMPT);
        e.setPromptTemplate(DEFAULT_PROMPT_TEMPLATE);
        e.setModel(null);
        e.setProviderId(null);
        e.setTemperature(0.4);
        e.setDefaultCount(DEFAULT_DEFAULT_COUNT);
        e.setMaxCount(DEFAULT_MAX_COUNT);
        e.setMaxContentChars(DEFAULT_MAX_CONTENT_CHARS);
        e.setHistoryEnabled(Boolean.TRUE);
        e.setHistoryKeepDays(30);
        e.setHistoryKeepRows(5000);
        e.setVersion(0);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(null);
        return e;
    }

    private PostSuggestionGenConfigEntity mergeAndValidate(PostSuggestionGenConfigEntity base, PostTagGenConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");

        String systemPrompt = payload.getSystemPrompt() == null ? "" : payload.getSystemPrompt().trim();
        String promptTemplate = payload.getPromptTemplate() == null ? "" : payload.getPromptTemplate().trim();
        if (systemPrompt.isBlank()) throw new IllegalArgumentException("systemPrompt 不能为空");
        if (promptTemplate.isBlank()) throw new IllegalArgumentException("promptTemplate 不能为空");
        if (promptTemplate.length() > 20000) throw new IllegalArgumentException("promptTemplate 过长（>20000），请精简");

        Integer defaultCount = payload.getDefaultCount();
        Integer maxCount = payload.getMaxCount();
        if (defaultCount == null) defaultCount = DEFAULT_DEFAULT_COUNT;
        if (maxCount == null) maxCount = DEFAULT_MAX_COUNT;
        if (defaultCount < 1 || defaultCount > 50) throw new IllegalArgumentException("defaultCount 需在 [1,50] 范围内");
        if (maxCount < 1 || maxCount > 50) throw new IllegalArgumentException("maxCount 需在 [1,50] 范围内");
        if (defaultCount > maxCount) throw new IllegalArgumentException("defaultCount 不能大于 maxCount");

        Integer maxContentChars = payload.getMaxContentChars();
        if (maxContentChars == null) maxContentChars = DEFAULT_MAX_CONTENT_CHARS;
        if (maxContentChars < 200 || maxContentChars > 50000) throw new IllegalArgumentException("maxContentChars 需在 [200,50000] 范围内");

        Double temperature = payload.getTemperature();
        if (temperature != null && (temperature < 0 || temperature > 2)) {
            throw new IllegalArgumentException("temperature 需在 [0,2] 范围内");
        }

        Integer historyKeepDays = payload.getHistoryKeepDays();
        if (historyKeepDays != null && historyKeepDays < 1) throw new IllegalArgumentException("historyKeepDays 必须为正数");
        Integer historyKeepRows = payload.getHistoryKeepRows();
        if (historyKeepRows != null && historyKeepRows < 1) throw new IllegalArgumentException("historyKeepRows 必须为正数");

        base.setEnabled(Boolean.TRUE.equals(payload.getEnabled()));
        base.setSystemPrompt(systemPrompt);
        base.setPromptTemplate(promptTemplate);

        String model = payload.getModel();
        base.setModel(model == null || model.isBlank() ? null : model.trim());
        String providerId = payload.getProviderId();
        base.setProviderId(providerId == null || providerId.isBlank() ? null : providerId.trim());
        base.setTemperature(temperature);

        base.setDefaultCount(defaultCount);
        base.setMaxCount(maxCount);
        base.setMaxContentChars(maxContentChars);

        base.setHistoryEnabled(Boolean.TRUE.equals(payload.getHistoryEnabled()));
        base.setHistoryKeepDays(historyKeepDays);
        base.setHistoryKeepRows(historyKeepRows);
        return base;
    }

    private PostTagGenConfigDTO toDto(PostSuggestionGenConfigEntity e, String updatedByName) {
        PostTagGenConfigDTO dto = new PostTagGenConfigDTO();
        dto.setId(e.getId());
        dto.setVersion(e.getVersion());
        dto.setEnabled(e.getEnabled());
        dto.setSystemPrompt(e.getSystemPrompt());
        dto.setPromptTemplate(e.getPromptTemplate());
        dto.setModel(e.getModel());
        dto.setProviderId(e.getProviderId());
        dto.setTemperature(e.getTemperature());
        dto.setDefaultCount(e.getDefaultCount());
        dto.setMaxCount(e.getMaxCount());
        dto.setMaxContentChars(e.getMaxContentChars());
        dto.setHistoryEnabled(e.getHistoryEnabled());
        dto.setHistoryKeepDays(e.getHistoryKeepDays());
        dto.setHistoryKeepRows(e.getHistoryKeepRows());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private PostTagGenHistoryDTO toHistoryDto(PostSuggestionGenHistoryEntity e) {
        PostTagGenHistoryDTO dto = new PostTagGenHistoryDTO();
        dto.setId(e.getId());
        dto.setUserId(e.getUserId());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setBoardName(e.getBoardName());
        dto.setTitleExcerpt(e.getTitleExcerpt());

        dto.setRequestedCount(e.getRequestedCount());
        dto.setAppliedMaxContentChars(e.getAppliedMaxContentChars());
        dto.setContentLen(e.getContentLen());
        dto.setContentExcerpt(e.getContentExcerpt());

        dto.setModel(e.getModel());
        dto.setTemperature(e.getTemperature());
        dto.setLatencyMs(e.getLatencyMs());
        dto.setPromptVersion(e.getPromptVersion());

        dto.setTags(toStringList(e.getOutputJson()));
        return dto;
    }

    private static List<String> toStringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o == null) continue;
                String s = Objects.toString(o, "").trim();
                if (!s.isBlank()) out.add(s);
            }
            return out;
        }
        if (v instanceof Map<?, ?> map) {
            Object tags = map.get("tags");
            if (tags instanceof List<?> list) {
                return toStringList(list);
            }
        }
        return List.of();
    }
}
