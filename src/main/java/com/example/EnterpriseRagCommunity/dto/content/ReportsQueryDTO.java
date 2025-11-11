package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportsQueryDTO {
    @ApiModelProperty(value = "举报人ID", example = "100")
    private Long reporterId;

    @ApiModelProperty(value = "目标类型", example = "POST")
    private ReportTargetType targetType;

    @ApiModelProperty(value = "目标ID", example = "1000")
    private Long targetId;

    @ApiModelProperty(value = "原因编码", example = "SPAM")
    private String reasonCode;

    @ApiModelProperty(value = "原因描述", example = "Repeated spam content")
    private String reasonText;

    @ApiModelProperty(value = "处理状态", example = "PENDING")
    private ReportStatus status;

    @ApiModelProperty(value = "处理人ID", example = "2")
    private Long handledById;

    @ApiModelProperty(value = "处理说明", example = "Content removed due to violation")
    private String resolution;

    // 单值时间查询（可选）
    @ApiModelProperty(value = "创建时间", example = "2025-01-01T12:00:00")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "处理时间", example = "2025-01-02T12:00:00")
    private LocalDateTime handledAt;

    // 范围查询支持
    @ApiModelProperty(value = "创建时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime createdTo;

    @ApiModelProperty(value = "处理时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime handledFrom;

    @ApiModelProperty(value = "处理时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime handledTo;

    @ApiModelProperty(value = "页码，从1开始", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页大小", example = "20")
    private Integer pageSize = 20;

    @ApiModelProperty(value = "排序字段", example = "createdAt")
    private String sortBy;

    @ApiModelProperty(value = "排序方向 asc/desc", example = "desc")
    private String sortOrder = "desc";
}
