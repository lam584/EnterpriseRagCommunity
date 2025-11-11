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
public class ModerationActionsCreateDTO {
    @NotNull
    @ApiModelProperty(value = "所属队列ID", required = true, example = "100")
    private Long queueId;

    @ApiModelProperty(value = "操作者用户ID", example = "200")
    private Long actorUserId;

    @NotNull
    @ApiModelProperty(value = "动作类型", required = true, example = "APPROVE")
    private ActionType action;

    @ApiModelProperty(value = "理由/备注", example = "违规内容")
    @Size(max = 255)
    private String reason;

    @ApiModelProperty(value = "快照(JSON)")
    private Map<String, Object> snapshot;

    @NotNull
    @ApiModelProperty(value = "创建时间(后端填充)")
    @JsonIgnore
    private LocalDateTime createdAt;
}

