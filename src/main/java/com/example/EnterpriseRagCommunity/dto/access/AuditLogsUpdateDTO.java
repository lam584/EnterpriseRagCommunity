package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Data
public class AuditLogsUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("租户ID")
    private Optional<Long> tenantId = Optional.empty();

    @ApiModelProperty("操作者用户ID")
    private Optional<Long> actorUserId = Optional.empty();

    @ApiModelProperty("动作名称")
    private Optional<String> action = Optional.empty();

    @ApiModelProperty("实体类型")
    private Optional<String> entityType = Optional.empty();

    @ApiModelProperty("实体ID")
    private Optional<Long> entityId = Optional.empty();

    @ApiModelProperty("结果：SUCCESS/FAIL")
    private Optional<AuditResult> result = Optional.empty();

    @ApiModelProperty("详情 JSON")
    private Optional<Map<String, Object>> details = Optional.empty();

    @ApiModelProperty("创建时间（不可修改，系统填充）")
    @JsonIgnore
    private LocalDateTime createdAt; // present for read but ignore modifications
}

