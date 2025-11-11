package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
public class RagEvalResultsCreateDTO {

    @ApiModelProperty(value = "评测批次ID", required = true)
    @NotNull
    private Long runId;

    @ApiModelProperty(value = "样本ID", required = true)
    @NotNull
    private Long sampleId;

    @ApiModelProperty(value = "EM 指标")
    private Double em;

    @ApiModelProperty(value = "F1 指标")
    private Double f1;

    @ApiModelProperty(value = "命中率")
    private Double hitRate;

    @ApiModelProperty(value = "延迟（毫秒）")
    @PositiveOrZero
    private Integer latencyMs;

    @ApiModelProperty(value = "输入 Token")
    @PositiveOrZero
    private Integer tokensIn;

    @ApiModelProperty(value = "输出 Token")
    @PositiveOrZero
    private Integer tokensOut;

    @ApiModelProperty(value = "成本（分）")
    @PositiveOrZero
    private Integer costCents;

    @ApiModelProperty(value = "创建时间（数据库默认生成）")
    @JsonIgnore
    private LocalDateTime createdAt;
}

