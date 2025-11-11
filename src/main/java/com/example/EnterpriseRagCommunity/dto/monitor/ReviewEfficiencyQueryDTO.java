package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class ReviewEfficiencyQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "统计窗口开始")
    private LocalDateTime windowStart;

    @ApiModelProperty(value = "统计窗口结束")
    private LocalDateTime windowEnd;

    @ApiModelProperty(value = "样本总数")
    private Integer total;

    @ApiModelProperty(value = "人工参与占比(0-1)")
    private BigDecimal humanShare;

    @ApiModelProperty(value = "平均处理时延（毫秒）")
    private Integer avgLatencyMs;

    @ApiModelProperty(value = "记录创建时间")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "统计窗口开始-起")
    private LocalDateTime windowStartFrom;

    @ApiModelProperty(value = "统计窗口开始-止")
    private LocalDateTime windowStartTo;

    @ApiModelProperty(value = "统计窗口结束-起")
    private LocalDateTime windowEndFrom;

    @ApiModelProperty(value = "统计窗口结束-止")
    private LocalDateTime windowEndTo;

    @ApiModelProperty(value = "记录创建时间-起")
    private LocalDateTime createdAtFrom;

    @ApiModelProperty(value = "记录创建时间-止")
    private LocalDateTime createdAtTo;

    @ApiModelProperty(value = "聚合维度", example = "DATE")
    private String groupBy; // NONE|HOUR|DATE

    public ReviewEfficiencyQueryDTO() {
        this.setOrderBy("createdAt");
        this.setSort("desc");
    }
}
