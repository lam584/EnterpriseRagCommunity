package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ActionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ModerationActionsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "主键ID", required = true, example = "1")
    private Long id;

    @ApiModelProperty(value = "所属队列ID", example = "100")
    private Long queueId;

    @ApiModelProperty(value = "操作者用户ID", example = "200")
    private Long actorUserId;

    @ApiModelProperty(value = "动作类型", example = "APPROVE")
    private ActionType action;

    @ApiModelProperty(value = "理由/备注", example = "违规内容")
    private @Size(max = 255) String reason;

    @ApiModelProperty(value = "快照(JSON)")
    private Map<String, Object> snapshot;

    @ApiModelProperty(value = "创建时间(不可修改)")
    @JsonIgnore
    private LocalDateTime createdAt;
}

