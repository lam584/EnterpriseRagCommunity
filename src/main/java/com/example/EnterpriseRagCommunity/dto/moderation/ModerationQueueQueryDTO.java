package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class ModerationQueueQueryDTO extends PageRequestDTO {
    @ApiModelProperty(value = "主键ID", example = "100")
    private Long id;

    @ApiModelProperty(value = "内容类型", example = "POST")
    private ContentType contentType;

    @ApiModelProperty(value = "内容ID", example = "123")
    private Long contentId;

    @ApiModelProperty(value = "状态", example = "PENDING")
    private QueueStatus status;

    @ApiModelProperty(value = "当前阶段", example = "RULE")
    private QueueStage currentStage;

    @ApiModelProperty(value = "指派给用户ID", example = "200")
    private Long assignedToId;

    @ApiModelProperty(value = "优先级", example = "5")
    private Integer priority;

    @ApiModelProperty(value = "优先级下限", example = "1")
    private Integer minPriority;

    @ApiModelProperty(value = "优先级上限", example = "10")
    private Integer maxPriority;

    @ApiModelProperty(value = "创建时间", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime createdTo;

    @ApiModelProperty(value = "更新时间", example = "2025-01-01T00:00:00")
    private LocalDateTime updatedAt;

    @ApiModelProperty(value = "更新时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime updatedFrom;

    @ApiModelProperty(value = "更新时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime updatedTo;
}
