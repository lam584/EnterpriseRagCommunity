package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HotScoresQueryDTO {
    @ApiModelProperty(value = "帖子ID", example = "1000")
    private Long postId;

    // 单值精确匹配字段（可选）：
    @ApiModelProperty(value = "24小时热度分(精确)", example = "500.0")
    private Double score24h;
    @ApiModelProperty(value = "7天热度分(精确)", example = "1000.0")
    private Double score7d;
    @ApiModelProperty(value = "累计热度分(精确)", example = "5500.0")
    private Double scoreAll;
    @ApiModelProperty(value = "衰减基数(精确)", example = "0.85")
    private Double decayBase;
    @ApiModelProperty(value = "最后重算时间(精确)", example = "2025-01-01T12:00:00")
    private LocalDateTime lastRecalculatedAt;

    // 范围查询字段（命名统一使用 Min/Max 保持与现有 DTO 风格一致）
    @ApiModelProperty(value = "24小时热度下限", example = "0")
    private Double score24hMin;
    @ApiModelProperty(value = "24小时热度上限", example = "1000")
    private Double score24hMax;

    @ApiModelProperty(value = "7天热度下限", example = "0")
    private Double score7dMin;
    @ApiModelProperty(value = "7天热度上限", example = "10000")
    private Double score7dMax;

    @ApiModelProperty(value = "总热度下限", example = "0")
    private Double scoreAllMin;
    @ApiModelProperty(value = "总热度上限", example = "100000")
    private Double scoreAllMax;

    @ApiModelProperty(value = "衰减基数下限", example = "0.5")
    private Double decayBaseMin;
    @ApiModelProperty(value = "衰减基数上限", example = "0.95")
    private Double decayBaseMax;

    @ApiModelProperty(value = "重算时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime lastRecalculatedFrom;
    @ApiModelProperty(value = "重算时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime lastRecalculatedTo;

    // 分页与排序
    @ApiModelProperty(value = "页码，从1开始", example = "1")
    private Integer page = 1;
    @ApiModelProperty(value = "每页大小", example = "20")
    private Integer pageSize = 20;
    @ApiModelProperty(value = "排序字段", example = "score24h")
    private String sortBy;
    @ApiModelProperty(value = "排序方向 asc/desc", example = "desc")
    private String sortOrder = "desc";
}
