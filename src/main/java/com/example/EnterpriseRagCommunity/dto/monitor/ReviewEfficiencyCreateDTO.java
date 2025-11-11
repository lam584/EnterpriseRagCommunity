package com.example.EnterpriseRagCommunity.dto.monitor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReviewEfficiencyCreateDTO {

    @ApiModelProperty(value = "统计窗口开始", required = true)
    @NotNull
    private LocalDateTime windowStart;

    @ApiModelProperty(value = "统计窗口结束", required = true)
    @NotNull
    private LocalDateTime windowEnd;

    @ApiModelProperty(value = "样本总数", required = true)
    @NotNull
    @PositiveOrZero
    private Integer total;

    @ApiModelProperty(value = "人工参与占比(0-1)", required = true, example = "0.1234")
    @NotNull
    private BigDecimal humanShare;

    @ApiModelProperty(value = "平均处理时延（毫秒）", required = true)
    @NotNull
    @PositiveOrZero
    private Integer avgLatencyMs;

    @ApiModelProperty(value = "创建时间（数据库默认生成）")
    @JsonIgnore
    private LocalDateTime createdAt;
}

