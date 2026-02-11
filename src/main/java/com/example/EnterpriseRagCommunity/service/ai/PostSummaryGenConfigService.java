package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenConfigEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.ai.PostSummaryGenConfigRepository;
import com.example.EnterpriseRagCommunity.repository.ai.PostSummaryGenHistoryRepository;
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

    public static final String DEFAULT_PROMPT_TEMPLATE = """
请为以下社区帖子生成“帖子摘要”。
要求：
- 只输出严格 JSON，不要输出任何解释文字，不要包裹 ```；
- JSON 字段：{"title":"...","summary":"..."}；
- title：可选，若原文标题已足够清晰可直接复用或略微改写；
- summary：中文摘要，建议 80~200 字，尽量覆盖关键信息、结论与可执行要点；

帖子标题：
{{title}}

帖子正文：
{{content}}
""";

    private final PostSummaryGenConfigRepository configRepository;
    private final PostSummaryGenHistoryRepository historyRepository;

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
        int safeSize = Math.min(100, Math.max(1, size));
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
        e.setModel(null);
        e.setProviderId(null);
        e.setTemperature(0.3);
        e.setTopP(0.7);
        e.setEnableThinking(Boolean.FALSE);
        e.setMaxContentChars(DEFAULT_MAX_CONTENT_CHARS);
        e.setPromptTemplate(DEFAULT_PROMPT_TEMPLATE);
        e.setVersion(0);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(null);
        return e;
    }

    private PostSummaryGenConfigEntity mergeAndValidate(PostSummaryGenConfigEntity base, PostSummaryGenConfigDTO payload) {
        if (payload == null) throw new IllegalArgumentException("payload 不能为空");

        String promptTemplate = payload.getPromptTemplate() == null ? "" : payload.getPromptTemplate().trim();
        if (promptTemplate.isBlank()) throw new IllegalArgumentException("promptTemplate 不能为空");
        if (promptTemplate.length() > 20000) throw new IllegalArgumentException("promptTemplate 过长（>20000），请精简");

        Integer maxContentChars = payload.getMaxContentChars();
        if (maxContentChars == null) maxContentChars = DEFAULT_MAX_CONTENT_CHARS;
        if (maxContentChars < 200 || maxContentChars > 50000) throw new IllegalArgumentException("maxContentChars 需在 [200,50000] 范围内");

        Double temperature = payload.getTemperature();
        if (temperature != null && (temperature < 0 || temperature > 1)) {
            throw new IllegalArgumentException("temperature 需在 [0,1] 范围内");
        }

        Double topP = payload.getTopP();
        if (topP != null && (topP < 0 || topP > 1)) {
            throw new IllegalArgumentException("topP 需在 [0,1] 范围内");
        }

        String model = payload.getModel();
        base.setModel(model == null || model.isBlank() ? null : model.trim());
        String providerId = payload.getProviderId();
        base.setProviderId(providerId == null || providerId.isBlank() ? null : providerId.trim());

        base.setEnabled(Boolean.TRUE.equals(payload.getEnabled()));
        base.setTemperature(temperature);
        base.setTopP(topP);
        base.setEnableThinking(Boolean.TRUE.equals(payload.getEnableThinking()));
        base.setMaxContentChars(maxContentChars);
        base.setPromptTemplate(promptTemplate);
        return base;
    }

    private PostSummaryGenConfigDTO toDto(PostSummaryGenConfigEntity e, String updatedByName) {
        PostSummaryGenConfigDTO dto = new PostSummaryGenConfigDTO();
        dto.setId(e.getId());
        dto.setVersion(e.getVersion());
        dto.setEnabled(e.getEnabled());
        dto.setModel(e.getModel());
        dto.setProviderId(e.getProviderId());
        dto.setTemperature(e.getTemperature());
        dto.setTopP(e.getTopP());
        dto.setEnableThinking(e.getEnableThinking());
        dto.setMaxContentChars(e.getMaxContentChars());
        dto.setPromptTemplate(e.getPromptTemplate());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setUpdatedBy(updatedByName);
        return dto;
    }

    private PostSummaryGenHistoryDTO toHistoryDto(PostSummaryGenHistoryEntity e) {
        PostSummaryGenHistoryDTO dto = new PostSummaryGenHistoryDTO();
        dto.setId(e.getId());
        dto.setPostId(e.getPostId());
        dto.setStatus(e.getStatus());
        dto.setModel(e.getModel());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setLatencyMs(e.getLatencyMs());
        dto.setErrorMessage(e.getErrorMessage());
        return dto;
    }
}
