package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModerationQueueUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "队列ID", required = true, example = "100")
    private Long id;

    @ApiModelProperty(value = "内容类型", example = "POST")
    private ContentType contentType;

    @ApiModelProperty(value = "内容ID", example = "123")
    private Long contentId;

    @ApiModelProperty(value = "状态", example = "REVIEWING")
    private QueueStatus status;

    @ApiModelProperty(value = "当前阶段", example = "RULE")
    private QueueStage currentStage;

    @ApiModelProperty(value = "优先级", example = "5")
    private Integer priority;

    @ApiModelProperty(value = "指派审核员用户ID", example = "200")
    private Long assignedToId;

    @ApiModelProperty(value = "创建时间(不可修改)")
    @JsonIgnore
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间(系统生成)")
    @JsonIgnore
    private LocalDateTime updatedAt;
}
