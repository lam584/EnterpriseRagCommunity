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
public class ModerationQueueCreateDTO {
    @NotNull
    @ApiModelProperty(value = "内容类型", required = true, example = "POST")
    private ContentType contentType;

    @NotNull
    @ApiModelProperty(value = "内容ID", required = true, example = "123")
    private Long contentId;

    @NotNull
    @ApiModelProperty(value = "队列状态", required = true, example = "PENDING")
    private QueueStatus status;

    @NotNull
    @ApiModelProperty(value = "当前阶段", required = true, example = "RULE")
    private QueueStage currentStage;

    @NotNull
    @ApiModelProperty(value = "优先级", required = true, example = "0")
    private Integer priority;

    @ApiModelProperty(value = "指派审核员用户ID", example = "200")
    private Long assignedToId;

    @NotNull
    @ApiModelProperty(value = "创建时间(后端填充)")
    @JsonIgnore
    private LocalDateTime createdAt;

    @NotNull
    @ApiModelProperty(value = "更新时间(后端填充)")
    @JsonIgnore
    private LocalDateTime updatedAt;
}

