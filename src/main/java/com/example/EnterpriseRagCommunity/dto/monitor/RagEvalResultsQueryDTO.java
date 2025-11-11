package com.example.EnterpriseRagCommunity.dto.monitor;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class RagEvalResultsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "评测批次ID", example = "1")
    private Long runId;

    @ApiModelProperty(value = "样本ID", example = "10")
    private Long sampleId;

    @ApiModelProperty(value = "EM 指标")
    private Double em;

    @ApiModelProperty(value = "F1 指标")
    private Double f1;

    @ApiModelProperty(value = "命中率")
    private Double hitRate;

    @ApiModelProperty(value = "延迟（毫秒）")
    private Integer latencyMs;

    @ApiModelProperty(value = "输入 Token")
    private Integer tokensIn;

    @ApiModelProperty(value = "输出 Token")
    private Integer tokensOut;

    @ApiModelProperty(value = "成本（分）")
    private Integer costCents;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdAt;

    // 范围过滤字段（后缀 After/Before, Min/Max）
    @ApiModelProperty(value = "创建时间-起")
    private LocalDateTime createdAfter;

    @ApiModelProperty(value = "创建时间-止")
    private LocalDateTime createdBefore;

    @ApiModelProperty(value = "EM 最小值")
    private Double emMin;

    @ApiModelProperty(value = "EM 最大值")
    private Double emMax;

    @ApiModelProperty(value = "F1 最小值")
    private Double f1Min;

    @ApiModelProperty(value = "F1 最大值")
    private Double f1Max;

    @ApiModelProperty(value = "命中率最小值")
    private Double hitRateMin;

    @ApiModelProperty(value = "命中率最大值")
    private Double hitRateMax;

    @ApiModelProperty(value = "延迟最小值（毫秒）")
    private Integer latencyMsMin;

    @ApiModelProperty(value = "延迟最大值（毫秒）")
    private Integer latencyMsMax;

    @ApiModelProperty(value = "输入 Token 最小值")
    private Integer tokensInMin;

    @ApiModelProperty(value = "输入 Token 最大值")
    private Integer tokensInMax;

    @ApiModelProperty(value = "输出 Token 最小值")
    private Integer tokensOutMin;

    @ApiModelProperty(value = "输出 Token 最大值")
    private Integer tokensOutMax;

    @ApiModelProperty(value = "成本（分）最小值")
    private Integer costCentsMin;

    @ApiModelProperty(value = "成本（分）最大值")
    private Integer costCentsMax;

    public RagEvalResultsQueryDTO() {
        this.setOrderBy("createdAt");
        this.setSort("desc");
    }
}
