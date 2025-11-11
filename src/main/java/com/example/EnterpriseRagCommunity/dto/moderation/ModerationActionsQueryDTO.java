package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ActionType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
@EqualsAndHashCode(callSuper = true)
@Data
public class ModerationActionsQueryDTO extends PageRequestDTO {
    @ApiModelProperty(value = "主键ID", example = "1")
    private Long id;

    @ApiModelProperty(value = "所属队列ID", example = "100")
    private Long queueId;

    @ApiModelProperty(value = "操作者用户ID", example = "200")
    private Long actorUserId;

    @ApiModelProperty(value = "动作类型", example = "APPROVE")
    private ActionType action;

    @ApiModelProperty(value = "理由/备注", example = "违规内容")
    private String reason;

    @ApiModelProperty(value = "快照(JSON)")
    private Map<String, Object> snapshot;

    @ApiModelProperty(value = "创建时间", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;

    // 范围查询
    @ApiModelProperty(value = "操作时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "操作时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime createdTo;
}
