package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminModerationQueueBackfillRequest {

    @ApiModelProperty(value = "要补齐的内容类型（默认 POST+COMMENT）", example = "[\"POST\",\"COMMENT\"]")
    private List<ContentType> contentTypes;

    @ApiModelProperty(value = "只扫描 createdAt >= createdFrom 的内容（可选）", example = "2025-01-01T00:00:00")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "只扫描 createdAt <= createdTo 的内容（可选）", example = "2025-12-31T23:59:59")
    private LocalDateTime createdTo;

    @ApiModelProperty(value = "本次最多新增入队条数（可选，默认 500，最大 5000）", example = "500")
    private Integer limit;

    @ApiModelProperty(value = "仅演练不落库（可选，默认 false）", example = "false")
    private Boolean dryRun;
}

