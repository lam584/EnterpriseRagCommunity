package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AuditLogsCreateDTO {
    // id excluded per规范 (auto generated)

    @ApiModelProperty("租户ID")
    private Long tenantId; // nullable

    @ApiModelProperty("操作者用户ID")
    private Long actorUserId; // nullable

    @ApiModelProperty("动作名称")
    @NotBlank
    private String action;

    @ApiModelProperty("实体类型")
    @NotBlank
    private String entityType;

    @ApiModelProperty("实体ID")
    private Long entityId; // nullable

    @ApiModelProperty("结果：SUCCESS/FAIL")
    @NotNull
    private AuditResult result;

    @ApiModelProperty("详情 JSON")
    private Map<String, Object> details; // nullable

    @ApiModelProperty("创建时间（系统填充）")
    @JsonIgnore // system default timestamp
    private LocalDateTime createdAt;
}

