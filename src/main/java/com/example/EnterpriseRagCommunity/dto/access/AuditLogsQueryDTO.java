package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@EqualsAndHashCode(callSuper = true)
@Data
public class AuditLogsQueryDTO extends PageRequestDTO {
    @ApiModelProperty("主键ID")
    private Long id; // added id for exact match

    @ApiModelProperty("租户ID")
    private Long tenantId;

    @ApiModelProperty("操作者用户ID")
    private Long actorUserId;

    @ApiModelProperty("动作（模糊匹配）")
    private String action;

    @ApiModelProperty("实体类型（模糊匹配）")
    private String entityType;

    @ApiModelProperty("实体ID")
    private Long entityId;

    @ApiModelProperty("结果（单值，精确匹配）")
    private AuditResult result; // changed from List to single value

    @ApiModelProperty("结果（多选）")
    private List<AuditResult> resultIn; // optional auxiliary multi-select

    @ApiModelProperty("创建时间（精确匹配）")
    private LocalDateTime createdAt; // add single value

    @ApiModelProperty("创建时间-起")
    private LocalDateTime createdAfter;

    @ApiModelProperty("创建时间-止")
    private LocalDateTime createdBefore;

    @ApiModelProperty("详情 JSON（保留字段）")
    private Map<String, Object> details;
}
