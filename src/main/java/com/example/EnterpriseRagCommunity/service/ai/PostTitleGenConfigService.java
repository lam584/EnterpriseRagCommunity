package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostTitleGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostTitleGenHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostTitleGenConfigRepository;
import com.example.EnterpriseRagCommunity.repository.ai.PostTitleGenHistoryRepository;
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
public class PostTitleGenConfigService {

    public static final int DEFAULT_DEFAULT_COUNT = 5;
    public static final int DEFAULT_MAX_COUNT = 10;
    public static final int DEFAULT_MAX_CONTENT_CHARS = 4000;

    public static final String DEFAULT_SYSTEM_PROMPT = "你是专业的中文社区运营编辑，擅长给帖子拟标题。";
    public static final String DEFAULT_PROMPT_TEMPLATE = """
请为下面这段社区帖子内容生成 {{count}} 个中文标题候选。
要求：
- 每个标题不超过 30 个汉字
- 风格适度多样（提问式/总结式/爆点式），但不要低俗
- 标题之间不要重复
- 只输出严格 JSON，不要输出任何解释文字
- JSON 格式：{"titles":["...", "..."]}

{{boardLine}}{{tagsLine}}帖子内容：
{{content}}
""";

    private final PostTitleGenConfigRepository configRepository;
    private final PostTitleGenHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public PostTitleGenConfigDTO getAdminConfig() {
        PostTitleGenConfigEntity cfg = configRepository.findTopByOrderByUpdatedAtDesc().orElse(null);
        if (cfg == null) return toDto(defaultEntity(), null);
        return toDto(cfg, null);
    }

    @Transactional(readOnly = true)
    public PostTitleGenPublicConfigDTO getPublicConfig() {
        PostTitleGenConfigEntity cfg = configRepository.findTopByOrderByUpdatedAtDesc().orElse(null);
        if (cfg == null) cfg = defaultEntity();

        PostTitleGenPublicConfigDTO dto = new PostTitleGenPublicConfigDTO();
        dto.setEnabled(Boolean.TRUE.equals(cfg.getEnabled()));
        dto.setDefaultCount(cfg.getDefaultCount());
        dto.setMaxCount(cfg.getMaxCount());
        return dto;
    }

    @Transactional
    public PostTitleGenConfigDTO upsertAdminConfig(PostTitleGenConfigDTO payload, Long actorUserId, String actorUsername) {
        PostTitleGenConfigEntity cfg = configRepository.findAll().stream().findFirst().orElseGet(this::defaultEntity);

        PostTitleGenConfigEntity merged = mergeAndValidate(cfg, payload);
        merged.setUpdatedAt(LocalDateTime.now());
        merged.setUpdatedBy(actorUserId);

        merged = configRepository.save(merged);
        return toDto(merged, actorUsername);
    }

    @Transactional(readOnly = true)
    public Page<PostTitleGenHistoryDTO> listHistory(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<PostTitleGenHistoryEntity> rows = (userId == null)
                ? historyRepository.findAllByOrderByCreatedAtDesc(pageable)
                : historyRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return rows.map(this::toHistoryDto);
    }

    @Transactional
    public void recordHistory(PostTitleGenHistoryEntity e) {
        if (e == null) return;
        historyRepository.save(e);
    }

    public PostTitleGenConfigEntity getConfigEntityOrDefault() {
        return configRepository.findTopByOrderByUpdatedAtDesc().orElseGet(this::defaultEntity);
    }

    private PostTitleGenConfigEntity defaultEntity() {
        PostTitleGenConfigEntity e = new PostTitleGenConfigEntity();
        e.setEnabled(Boolean.TRUE);
        e.setSystemPrompt(DEFAULT_SYSTEM_PROMPT);
        e.setPromptTemplate(DEFAULT_PROMPT_TEMPLATE);
        e.setModel(null);
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

    private PostTitleGenConfigEntity mergeAndValidate(PostTitleGenConfigEntity base, PostTitleGenConfigDTO payload) {
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
        base.setTemperature(temperature);

        base.setDefaultCount(defaultCount);
        base.setMaxCount(maxCount);
        base.setMaxContentChars(maxContentChars);

        base.setHistoryEnabled(Boolean.TRUE.equals(payload.getHistoryEnabled()));
        base.setHistoryKeepDays(historyKeepDays);
        base.setHistoryKeepRows(historyKeepRows);
        return base;
    }

    private PostTitleGenConfigDTO toDto(PostTitleGenConfigEntity e, String updatedByName) {
        PostTitleGenConfigDTO dto = new PostTitleGenConfigDTO();
        dto.setId(e.getId());
        dto.setVersion(e.getVersion());
        dto.setEnabled(e.getEnabled());
        dto.setSystemPrompt(e.getSystemPrompt());
        dto.setPromptTemplate(e.getPromptTemplate());
        dto.setModel(e.getModel());
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
    private PostTitleGenHistoryDTO toHistoryDto(PostTitleGenHistoryEntity e) {
        PostTitleGenHistoryDTO dto = new PostTitleGenHistoryDTO();
        dto.setId(e.getId());
        dto.setUserId(e.getUserId());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setBoardName(e.getBoardName());

        dto.setRequestedCount(e.getRequestedCount());
        dto.setAppliedMaxContentChars(e.getAppliedMaxContentChars());
        dto.setContentLen(e.getContentLen());
        dto.setContentExcerpt(e.getContentExcerpt());

        dto.setModel(e.getModel());
        dto.setTemperature(e.getTemperature());
        dto.setLatencyMs(e.getLatencyMs());
        dto.setPromptVersion(e.getPromptVersion());

        dto.setTags(toStringList(e.getTagsJson()));
        dto.setTitles(toStringList(e.getTitlesJson()));
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
            Object titles = map.get("titles");
            if (titles instanceof List<?> list) {
                return toStringList(list);
            }
        }
        return List.of();
    }
}

