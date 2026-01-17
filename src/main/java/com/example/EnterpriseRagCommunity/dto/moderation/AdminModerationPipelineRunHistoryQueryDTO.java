package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;

import java.time.LocalDateTime;

/**
 * @deprecated 当前历史查询使用 query params，无需该 DTO；保留作为后续改为 POST body/复杂筛选的扩展点。
 */
@Deprecated
public record AdminModerationPipelineRunHistoryQueryDTO(
        Long queueId,
        ContentType contentType,
        Long contentId,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        Integer page,
        Integer pageSize
) {
}
