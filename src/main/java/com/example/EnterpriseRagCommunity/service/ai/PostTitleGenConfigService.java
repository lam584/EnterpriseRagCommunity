package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SuggestionKind;
import com.example.EnterpriseRagCommunity.repository.ai.PostSuggestionGenConfigRepository;
import com.example.EnterpriseRagCommunity.repository.ai.PostSuggestionGenHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public static final String DEFAULT_PROMPT_CODE = "TITLE_GEN";

    private static final SuggestionKind KIND = SuggestionKind.TITLE;
    private static final String GROUP_CODE = "POST_SUGGESTION";

    private final PostSuggestionGenConfigRepository configRepository;
    private final PostSuggestionGenHistoryRepository historyRepository;
    private final PromptsRepository promptsRepository;

    @Transactional(readOnly = true)
    public PostTitleGenConfigDTO getAdminConfig() {
        PostSuggestionGenConfigEntity cfg = configRepository
                .findTopByGroupCodeAndKindOrderByUpdatedAtDesc(GROUP_CODE, KIND)
                .orElse(null);
        if (cfg == null) return toDto(defaultEntity(), null);
        return toDto(cfg, null);
    }

    @Transactional(readOnly = true)
    public PostTitleGenPublicConfigDTO getPublicConfig() {
        PostSuggestionGenConfigEntity cfg = configRepository
                .findTopByGroupCodeAndKindOrderByUpdatedAtDesc(GROUP_CODE, KIND)
                .orElse(null);
        if (cfg == null) cfg = defaultEntity();

        PostTitleGenPublicConfigDTO dto = new PostTitleGenPublicConfigDTO();
        dto.setEnabled(Boolean.TRUE.equals(cfg.getEnabled()));
        dto.setDefaultCount(cfg.getDefaultCount());
        dto.setMaxCount(cfg.getMaxCount());
        return dto;
    }

    @Transactional
    public PostTitleGenConfigDTO upsertAdminConfig(PostTitleGenConfigDTO payload, Long actorUserId, String actorUsername) {
        PostSuggestionGenConfigEntity cfg = configRepository
                .findTopByGroupCodeAndKindOrderByUpdatedAtDesc(GROUP_CODE, KIND)
                .orElseGet(this::defaultEntity);

        PostSuggestionGenConfigEntity merged = mergeAndValidate(cfg, payload);
        merged = PostSuggestionConfigSupport.saveUpdatedConfig(merged, actorUserId, configRepository::save);
        return toDto(merged, actorUsername);
    }

    @Transactional(readOnly = true)
    public Page<PostTitleGenHistoryDTO> listHistory(Long userId, int page, int size) {
        var pageable = PostSuggestionConfigSupport.buildHistoryPageable(page, size);

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
        return PostSuggestionGenConfigSupport.defaultEntity(
                GROUP_CODE,
                KIND,
                DEFAULT_PROMPT_CODE,
                DEFAULT_DEFAULT_COUNT,
                DEFAULT_MAX_COUNT,
                DEFAULT_MAX_CONTENT_CHARS
        );
    }

    private PostSuggestionGenConfigEntity mergeAndValidate(PostSuggestionGenConfigEntity base, PostTitleGenConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");

        String promptCode = payload.getPromptCode();
        if (promptCode == null || promptCode.isBlank()) {
            throw new IllegalArgumentException("promptCode 不能为空");
        }
        if (promptCode.length() > 64) {
             throw new IllegalArgumentException("promptCode 长度不能超过 64");
        }

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

        Integer historyKeepDays = payload.getHistoryKeepDays();
        if (historyKeepDays != null && historyKeepDays < 1) throw new IllegalArgumentException("historyKeepDays 必须为正数");
        Integer historyKeepRows = payload.getHistoryKeepRows();
        if (historyKeepRows != null && historyKeepRows < 1) throw new IllegalArgumentException("historyKeepRows 必须为正数");

        base.setEnabled(Boolean.TRUE.equals(payload.getEnabled()));
        base.setPromptCode(promptCode);

        promptsRepository.findByPromptCode(promptCode)
            .orElseThrow(() -> new IllegalArgumentException("promptCode 不存在: " + promptCode));

        base.setDefaultCount(defaultCount);
        base.setMaxCount(maxCount);
        base.setMaxContentChars(maxContentChars);

        base.setHistoryEnabled(Boolean.TRUE.equals(payload.getHistoryEnabled()));
        base.setHistoryKeepDays(historyKeepDays);
        base.setHistoryKeepRows(historyKeepRows);
        return base;
    }

    private PostTitleGenConfigDTO toDto(PostSuggestionGenConfigEntity e, String updatedByName) {
        return PostSuggestionGenConfigSupport.toTitleAdminConfigDto(e, updatedByName, promptsRepository::findByPromptCode);
    }

    private PostTitleGenHistoryDTO toHistoryDto(PostSuggestionGenHistoryEntity e) {
        PostTitleGenHistoryDTO dto = new PostTitleGenHistoryDTO();
        dto.setId(e.getId());
        dto.setUserId(e.getUserId());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setBoardName(e.getBoardName());

        dto.setRequestedCount(e.getRequestedCount());
        dto.setAppliedMaxContentChars(e.getAppliedMaxContentChars());
        dto.setContentLen(e.getContentLen());
        dto.setContentExcerpt(e.getContentExcerpt());

        dto.setTags(toStringList(e.getInputTagsJson()));
        dto.setTitles(toStringList(e.getOutputJson()));
        return dto;
    }

    private static List<String> toStringList(Object v) {
        switch (v) {
            case null -> {
                return List.of();
            }
            case List<?> list -> {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    if (o == null) continue;
                    String s = Objects.toString(o, "").trim();
                    if (!s.isBlank()) out.add(s);
                }
                return out;
            }
            case Map<?, ?> map -> {
                Object titles = map.get("titles");
                if (titles instanceof List<?> list) {
                    return toStringList(list);
                }
            }
            default -> {
            }
        }
        return List.of();
    }
}
