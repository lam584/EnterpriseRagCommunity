package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenHistoryEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostSummaryGenConfigRepository;
import com.example.EnterpriseRagCommunity.repository.ai.PostSummaryGenHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PostSummaryGenConfigService {

    public static final int DEFAULT_MAX_CONTENT_CHARS = 4000;
    private static final String GROUP_CODE = "POST_SUMMARY";
    private static final String SUB_TYPE = "DEFAULT";
    public static final String DEFAULT_PROMPT_CODE = "SUMMARY_GEN";

    private final PostSummaryGenConfigRepository configRepository;
    private final PostSummaryGenHistoryRepository historyRepository;
    private final PromptsRepository promptsRepository;

    @Transactional(readOnly = true)
    public PostSummaryGenConfigDTO getAdminConfig() {
        PostSummaryGenConfigEntity cfg = configRepository
                .findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(GROUP_CODE, SUB_TYPE)
                .orElse(null);
        if (cfg == null) return toDto(defaultEntity(), null);
        return toDto(cfg, null);
    }

    @Transactional(readOnly = true)
    public PostSummaryGenPublicConfigDTO getPublicConfig() {
        PostSummaryGenConfigEntity cfg = configRepository
                .findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(GROUP_CODE, SUB_TYPE)
                .orElse(null);
        if (cfg == null) cfg = defaultEntity();
        PostSummaryGenPublicConfigDTO dto = new PostSummaryGenPublicConfigDTO();
        dto.setEnabled(Boolean.TRUE.equals(cfg.getEnabled()));
        return dto;
    }

    @Transactional
    public PostSummaryGenConfigDTO upsertAdminConfig(PostSummaryGenConfigDTO payload, Long actorUserId, String actorUsername) {
        PostSummaryGenConfigEntity cfg = configRepository
                .findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(GROUP_CODE, SUB_TYPE)
                .orElseGet(this::defaultEntity);
        PostSummaryGenConfigEntity merged = mergeAndValidate(cfg, payload);
        merged.setUpdatedAt(LocalDateTime.now());
        merged.setUpdatedBy(actorUserId);
        merged = configRepository.save(merged);
        return toDto(merged, actorUsername);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryGenHistoryDTO> listHistory(Long postId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        if (postId == null) {
            return historyRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toHistoryDto);
        }
        return historyRepository.findByPostIdOrderByCreatedAtDesc(postId, pageable).map(this::toHistoryDto);
    }

    @Transactional
    public void recordHistory(PostSummaryGenHistoryEntity e) {
        if (e == null) return;
        historyRepository.save(e);
    }

    public PostSummaryGenConfigEntity getConfigEntityOrDefault() {
        return configRepository
                .findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(GROUP_CODE, SUB_TYPE)
                .orElseGet(this::defaultEntity);
    }

    private PostSummaryGenConfigEntity defaultEntity() {
        PostSummaryGenConfigEntity e = new PostSummaryGenConfigEntity();
        e.setGroupCode(GROUP_CODE);
        e.setSubType(SUB_TYPE);
        e.setEnabled(Boolean.TRUE);
        e.setMaxContentChars(DEFAULT_MAX_CONTENT_CHARS);
        e.setPromptCode(DEFAULT_PROMPT_CODE);
        e.setVersion(0);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(null);
        return e;
    }

    private PostSummaryGenConfigEntity mergeAndValidate(PostSummaryGenConfigEntity base, PostSummaryGenConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");
        PromptConfigValidationSupport.ValidatedPromptConfig validated =
                PromptConfigValidationSupport.validatePromptCodeAndMaxContentChars(
                        payload.getPromptCode(),
                        payload.getMaxContentChars(),
                        DEFAULT_MAX_CONTENT_CHARS,
                        50000
                );

        base.setEnabled(Boolean.TRUE.equals(payload.getEnabled()));
        base.setMaxContentChars(validated.maxContentChars());
        base.setPromptCode(validated.promptCode());

        promptsRepository.findByPromptCode(validated.promptCode())
            .orElseThrow(() -> new IllegalArgumentException("promptCode 不存在: " + validated.promptCode()));
        return base;
    }

    private PostSummaryGenConfigDTO toDto(PostSummaryGenConfigEntity e, String updatedByName) {
        PostSummaryGenConfigDTO dto = new PostSummaryGenConfigDTO();
        dto.setId(e.getId());
        dto.setVersion(e.getVersion());
        dto.setEnabled(e.getEnabled());
        PromptsEntity prompt = (e.getPromptCode() == null || e.getPromptCode().isBlank())
            ? null
            : promptsRepository.findByPromptCode(e.getPromptCode()).orElse(null);
        dto.setModel(prompt != null ? prompt.getModelName() : null);
        dto.setProviderId(prompt != null ? prompt.getProviderId() : null);
        dto.setTemperature(prompt != null ? prompt.getTemperature() : null);
        dto.setTopP(prompt != null ? prompt.getTopP() : null);
        dto.setEnableThinking(prompt != null ? prompt.getEnableDeepThinking() : null);
        dto.setMaxContentChars(e.getMaxContentChars());
        dto.setPromptCode(e.getPromptCode());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }

    private PostSummaryGenHistoryDTO toHistoryDto(PostSummaryGenHistoryEntity e) {
        PostSummaryGenHistoryDTO dto = new PostSummaryGenHistoryDTO();
        dto.setId(e.getId());
        dto.setPostId(e.getPostId());
        dto.setStatus(e.getStatus());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setErrorMessage(e.getErrorMessage());
        return dto;
    }
}
