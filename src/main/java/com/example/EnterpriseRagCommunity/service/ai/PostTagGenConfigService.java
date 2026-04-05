package com.example.EnterpriseRagCommunity.service.ai;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SuggestionKind;
import com.example.EnterpriseRagCommunity.repository.ai.PostSuggestionGenConfigRepository;
import com.example.EnterpriseRagCommunity.repository.ai.PostSuggestionGenHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostTagGenConfigService {

    public static final int DEFAULT_DEFAULT_COUNT = 5;
    public static final int DEFAULT_MAX_COUNT = 10;
    public static final int DEFAULT_MAX_CONTENT_CHARS = 4000;
    public static final String DEFAULT_PROMPT_CODE = "TAG_GEN";

    private static final SuggestionKind KIND = SuggestionKind.TOPIC_TAG;
    private static final String GROUP_CODE = "POST_SUGGESTION";

    private final PostSuggestionGenConfigRepository configRepository;
    private final PostSuggestionGenHistoryRepository historyRepository;
    private final PromptsRepository promptsRepository;

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
        merged = PostSuggestionConfigSupport.saveUpdatedConfig(merged, actorUserId, configRepository::save);
        return toDto(merged, actorUsername);
    }

    @Transactional(readOnly = true)
    public Page<PostTagGenHistoryDTO> listHistory(Long userId, int page, int size) {
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

    private PostSuggestionGenConfigEntity mergeAndValidate(PostSuggestionGenConfigEntity base, PostTagGenConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");

        PromptConfigValidationSupport.ValidatedPromptConfig validatedPrompt =
                PromptConfigValidationSupport.validatePromptCodeAndMaxContentChars(
                        payload.getPromptCode(),
                        payload.getMaxContentChars(),
                        DEFAULT_MAX_CONTENT_CHARS,
                        50000
                );
        String promptCode = validatedPrompt.promptCode();
        int maxContentChars = validatedPrompt.maxContentChars();
        int defaultCount = PostSuggestionGenConfigSupport.resolveCount(payload.getDefaultCount(), DEFAULT_DEFAULT_COUNT, "defaultCount");
        int maxCount = PostSuggestionGenConfigSupport.resolveCount(payload.getMaxCount(), DEFAULT_MAX_COUNT, "maxCount");
        if (defaultCount > maxCount) throw new IllegalArgumentException("defaultCount 不能大于 maxCount");

        Integer historyKeepDays = payload.getHistoryKeepDays();
        Integer historyKeepRows = payload.getHistoryKeepRows();
        PostSuggestionGenConfigSupport.validatePositiveNullable(historyKeepDays, "historyKeepDays");
        PostSuggestionGenConfigSupport.validatePositiveNullable(historyKeepRows, "historyKeepRows");

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

    private PostTagGenConfigDTO toDto(PostSuggestionGenConfigEntity e, String updatedByName) {
        return PostSuggestionGenConfigSupport.toTagAdminConfigDto(e, updatedByName, promptsRepository::findByPromptCode);
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

        dto.setTags(toStringList(e.getOutputJson()));
        return dto;
    }

    private static List<String> toStringList(Object v) {
        return PostSuggestionGenConfigSupport.toStringList(v, "tags");
    }
}
