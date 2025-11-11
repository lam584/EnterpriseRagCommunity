package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportsCreateDTO {
    @NotNull
    @ApiModelProperty(value = "举报人ID", required = true, example = "100")
    private Long reporterId;

    @NotNull
    @ApiModelProperty(value = "目标类型", required = true, example = "POST")
    private ReportTargetType targetType;

    @NotNull
    @ApiModelProperty(value = "目标ID", required = true, example = "1000")
    private Long targetId;

    @NotBlank
    @Size(max = 64)
    @ApiModelProperty(value = "原因编码", required = true, example = "SPAM")
    private String reasonCode;

    @Size(max = 255)
    @ApiModelProperty(value = "原因描述", example = "Repeated spam content")
    private String reasonText;

    // NOT NULL；可由后端/DB 默认，不从前端接收
    @NotNull
    @ApiModelProperty(value = "处理状态，默认PENDING", example = "PENDING")
    @JsonIgnore
    private ReportStatus status = ReportStatus.PENDING;

    @ApiModelProperty(value = "处理人ID", example = "2")
    private Long handledById;

    @ApiModelProperty(value = "处理时间", example = "2025-11-06T10:00:00")
    private LocalDateTime handledAt;

    @Size(max = 255)
    @ApiModelProperty(value = "处理说明", example = "Content removed due to violation")
    private String resolution;

    // 审计字段：由DB默认填充，但需显式映射
    @ApiModelProperty(value = "创建时间（DB填充）", example = "2025-11-06T10:00:00")
    @JsonIgnore
    private LocalDateTime createdAt;
}
