package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AuditLogsUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("租户ID")
    private Long tenantId;
    @JsonIgnore
    private boolean hasTenantId;

    @ApiModelProperty("操作者用户ID")
    private Long actorUserId;
    @JsonIgnore
    private boolean hasActorUserId;

    @ApiModelProperty("动作名称")
    private String action;
    @JsonIgnore
    private boolean hasAction;

    @ApiModelProperty("实体类型")
    private String entityType;
    @JsonIgnore
    private boolean hasEntityType;

    @ApiModelProperty("实体ID")
    private Long entityId;
    @JsonIgnore
    private boolean hasEntityId;

    @ApiModelProperty("结果：SUCCESS/FAIL")
    private AuditResult result;
    @JsonIgnore
    private boolean hasResult;

    @ApiModelProperty("详情 JSON")
    private Map<String, Object> details;
    @JsonIgnore
    private boolean hasDetails;

    @ApiModelProperty("创建时间（不可修改，系统填充）")
    @JsonIgnore
    private LocalDateTime createdAt; // present for read but ignore modifications

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
        this.hasTenantId = true;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
        this.hasActorUserId = true;
    }

    public void setAction(String action) {
        this.action = action;
        this.hasAction = true;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
        this.hasEntityType = true;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
        this.hasEntityId = true;
    }

    public void setResult(AuditResult result) {
        this.result = result;
        this.hasResult = true;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
        this.hasDetails = true;
    }
}

