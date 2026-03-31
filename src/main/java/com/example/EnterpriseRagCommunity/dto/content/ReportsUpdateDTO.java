package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "举报ID", required = true, example = "500")
    private Long id;

    // 不允许修改但需显式声明
    @ApiModelProperty(value = "举报人ID（不可修改）", example = "100")
    @JsonIgnore
    private Long reporterId;

    @ApiModelProperty(value = "目标类型（不可修改）", example = "POST")
    @JsonIgnore
    private ReportTargetType targetType;

    @ApiModelProperty(value = "目标ID（不可修改）", example = "1000")
    @JsonIgnore
    private Long targetId;

    @Size(max = 64)
    @ApiModelProperty(value = "原因编码", example = "SPAM")
    private String reasonCode;

    @Size(max = 255)
    @ApiModelProperty(value = "原因描述", example = "Repeated spam content")
    private String reasonText;

    @ApiModelProperty(value = "处理状态", example = "RESOLVED")
    private ReportStatus status;

    @Size(max = 255)
    @ApiModelProperty(value = "处理说明", example = "Content removed due to violation")
    private String resolution;

    @ApiModelProperty(value = "处理人ID", example = "2")
    private Long handledById;

    @ApiModelProperty(value = "处理时间，不传则由系统填充", example = "2025-11-06T10:00:00")
    private LocalDateTime handledAt;

    @ApiModelProperty(value = "创建时间（不可修改）", example = "2025-11-06T10:00:00")
    @JsonIgnore
    private LocalDateTime createdAt;
}
