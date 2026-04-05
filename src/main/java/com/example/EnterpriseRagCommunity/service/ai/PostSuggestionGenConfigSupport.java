package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostSuggestionGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.SuggestionKind;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class PostSuggestionGenConfigSupport {

    private static final AdminConfigDtoWriters<PostTagGenConfigDTO> TAG_DTO_WRITERS = new AdminConfigDtoWriters<>(
            PostTagGenConfigDTO::setId,
            PostTagGenConfigDTO::setVersion,
            PostTagGenConfigDTO::setEnabled,
            PostTagGenConfigDTO::setPromptCode,
            PostTagGenConfigDTO::setModel,
            PostTagGenConfigDTO::setProviderId,
            PostTagGenConfigDTO::setTemperature,
            PostTagGenConfigDTO::setTopP,
            PostTagGenConfigDTO::setEnableThinking,
            PostTagGenConfigDTO::setDefaultCount,
            PostTagGenConfigDTO::setMaxCount,
            PostTagGenConfigDTO::setMaxContentChars,
            PostTagGenConfigDTO::setHistoryEnabled,
            PostTagGenConfigDTO::setHistoryKeepDays,
            PostTagGenConfigDTO::setHistoryKeepRows,
            PostTagGenConfigDTO::setUpdatedAt,
            PostTagGenConfigDTO::setUpdatedBy
    );

    private static final AdminConfigDtoWriters<PostTitleGenConfigDTO> TITLE_DTO_WRITERS = new AdminConfigDtoWriters<>(
            PostTitleGenConfigDTO::setId,
            PostTitleGenConfigDTO::setVersion,
            PostTitleGenConfigDTO::setEnabled,
            PostTitleGenConfigDTO::setPromptCode,
            PostTitleGenConfigDTO::setModel,
            PostTitleGenConfigDTO::setProviderId,
            PostTitleGenConfigDTO::setTemperature,
            PostTitleGenConfigDTO::setTopP,
            PostTitleGenConfigDTO::setEnableThinking,
            PostTitleGenConfigDTO::setDefaultCount,
            PostTitleGenConfigDTO::setMaxCount,
            PostTitleGenConfigDTO::setMaxContentChars,
            PostTitleGenConfigDTO::setHistoryEnabled,
            PostTitleGenConfigDTO::setHistoryKeepDays,
            PostTitleGenConfigDTO::setHistoryKeepRows,
            PostTitleGenConfigDTO::setUpdatedAt,
            PostTitleGenConfigDTO::setUpdatedBy
    );

    private PostSuggestionGenConfigSupport() {
    }

    public static PostSuggestionGenConfigEntity defaultEntity(
            String groupCode,
            SuggestionKind kind,
            String promptCode,
            int defaultCount,
            int maxCount,
            int maxContentChars
    ) {
        PostSuggestionGenConfigEntity entity = new PostSuggestionGenConfigEntity();
        entity.setGroupCode(groupCode);
        entity.setKind(kind);
        entity.setEnabled(Boolean.TRUE);
        entity.setPromptCode(promptCode);
        entity.setDefaultCount(defaultCount);
        entity.setMaxCount(maxCount);
        entity.setMaxContentChars(maxContentChars);
        entity.setHistoryEnabled(Boolean.TRUE);
        entity.setHistoryKeepDays(30);
        entity.setHistoryKeepRows(5000);
        entity.setVersion(0);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(null);
        return entity;
    }

    public static int resolveCount(Integer value, int defaultValue, String fieldName) {
        int resolved = value == null ? defaultValue : value;
        if (resolved < 1 || resolved > 50) {
            throw new IllegalArgumentException(fieldName + " 需在 [1,50] 范围内");
        }
        return resolved;
    }

    public static void validatePositiveNullable(Integer value, String fieldName) {
        if (value != null && value < 1) {
            throw new IllegalArgumentException(fieldName + " 必须为正数");
        }
    }

    public static List<String> toStringList(Object value, String fieldName) {
        switch (value) {
            case null -> {
                return List.of();
            }
            case List<?> list -> {
                List<String> out = new ArrayList<>();
                for (Object item : list) {
                    if (item == null) continue;
                    String text = Objects.toString(item, "").trim();
                    if (!text.isBlank()) out.add(text);
                }
                return out;
            }
            case Map<?, ?> map -> {
                Object nested = map.get(fieldName);
                if (nested instanceof List<?> list) {
                    return toStringList(list, fieldName);
                }
            }
            default -> {
            }
        }
        return List.of();
    }

    public static PostTagGenConfigDTO toTagAdminConfigDto(
            PostSuggestionGenConfigEntity entity,
            String updatedByName,
            Function<String, Optional<PromptsEntity>> promptFinder
    ) {
        PostTagGenConfigDTO dto = new PostTagGenConfigDTO();
        applyAdminConfigDto(dto, resolveAdminConfigView(entity, updatedByName, promptFinder), TAG_DTO_WRITERS);
        return dto;
    }

    public static PostTitleGenConfigDTO toTitleAdminConfigDto(
            PostSuggestionGenConfigEntity entity,
            String updatedByName,
            Function<String, Optional<PromptsEntity>> promptFinder
    ) {
        PostTitleGenConfigDTO dto = new PostTitleGenConfigDTO();
        applyAdminConfigDto(dto, resolveAdminConfigView(entity, updatedByName, promptFinder), TITLE_DTO_WRITERS);
        return dto;
    }

    private static AdminConfigView resolveAdminConfigView(
            PostSuggestionGenConfigEntity entity,
            String updatedByName,
            Function<String, Optional<PromptsEntity>> promptFinder
    ) {
        PromptsEntity prompt = resolvePrompt(entity == null ? null : entity.getPromptCode(), promptFinder);
        if (entity != null) {
            return new AdminConfigView(
                    entity.getId(),
                    entity.getVersion(),
                    entity.getEnabled(),
                    entity.getPromptCode(),
                    prompt == null ? null : prompt.getModelName(),
                    prompt == null ? null : prompt.getProviderId(),
                    prompt == null ? null : prompt.getTemperature(),
                    prompt == null ? null : prompt.getTopP(),
                    prompt == null ? null : prompt.getEnableDeepThinking(),
                    entity.getDefaultCount(),
                    entity.getMaxCount(),
                    entity.getMaxContentChars(),
                    entity.getHistoryEnabled(),
                    entity.getHistoryKeepDays(),
                    entity.getHistoryKeepRows(),
                    entity.getUpdatedAt(),
                    updatedByName
            );
        }
                return new AdminConfigView(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    updatedByName
                );
    }

    private static PromptsEntity resolvePrompt(
            String promptCode,
            Function<String, Optional<PromptsEntity>> promptFinder
    ) {
        if (promptCode == null || promptCode.isBlank()) return null;
        return promptFinder.apply(promptCode).orElse(null);
    }

    private static <T> void applyAdminConfigDto(T dto, AdminConfigView view, AdminConfigDtoWriters<T> writers) {
        writers.setId().accept(dto, view.id());
        writers.setVersion().accept(dto, view.version());
        writers.setEnabled().accept(dto, view.enabled());
        writers.setPromptCode().accept(dto, view.promptCode());
        writers.setModel().accept(dto, view.model());
        writers.setProviderId().accept(dto, view.providerId());
        writers.setTemperature().accept(dto, view.temperature());
        writers.setTopP().accept(dto, view.topP());
        writers.setEnableThinking().accept(dto, view.enableThinking());
        writers.setDefaultCount().accept(dto, view.defaultCount());
        writers.setMaxCount().accept(dto, view.maxCount());
        writers.setMaxContentChars().accept(dto, view.maxContentChars());
        writers.setHistoryEnabled().accept(dto, view.historyEnabled());
        writers.setHistoryKeepDays().accept(dto, view.historyKeepDays());
        writers.setHistoryKeepRows().accept(dto, view.historyKeepRows());
        writers.setUpdatedAt().accept(dto, view.updatedAt());
        writers.setUpdatedBy().accept(dto, view.updatedBy());
    }

    private record AdminConfigView(
            Long id,
            Integer version,
            Boolean enabled,
            String promptCode,
            String model,
            String providerId,
            Double temperature,
            Double topP,
            Boolean enableThinking,
            Integer defaultCount,
            Integer maxCount,
            Integer maxContentChars,
            Boolean historyEnabled,
            Integer historyKeepDays,
            Integer historyKeepRows,
            LocalDateTime updatedAt,
            String updatedBy
    ) {
    }

    private record AdminConfigDtoWriters<T>(
            BiConsumer<T, Long> setId,
            BiConsumer<T, Integer> setVersion,
            BiConsumer<T, Boolean> setEnabled,
            BiConsumer<T, String> setPromptCode,
            BiConsumer<T, String> setModel,
            BiConsumer<T, String> setProviderId,
            BiConsumer<T, Double> setTemperature,
            BiConsumer<T, Double> setTopP,
            BiConsumer<T, Boolean> setEnableThinking,
            BiConsumer<T, Integer> setDefaultCount,
            BiConsumer<T, Integer> setMaxCount,
            BiConsumer<T, Integer> setMaxContentChars,
            BiConsumer<T, Boolean> setHistoryEnabled,
            BiConsumer<T, Integer> setHistoryKeepDays,
            BiConsumer<T, Integer> setHistoryKeepRows,
            BiConsumer<T, LocalDateTime> setUpdatedAt,
            BiConsumer<T, String> setUpdatedBy
    ) {
    }
}
