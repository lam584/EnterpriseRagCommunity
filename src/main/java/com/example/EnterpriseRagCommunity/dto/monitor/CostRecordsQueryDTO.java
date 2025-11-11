package com.example.EnterpriseRagCommunity.dto.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.enums.CostScope;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class CostRecordsQueryDTO extends com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO {
    @ApiModelProperty(value = "用途/范围", example = "GEN")
    private CostScope scope; // GEN|RERANK|MODERATION|OTHER

    @ApiModelProperty(value = "模型名称", example = "gpt-4o-mini")
    private String model;

    @ApiModelProperty(value = "输入Token")
    private Integer tokensIn;

    @ApiModelProperty(value = "输出Token")
    private Integer tokensOut;

    @ApiModelProperty(value = "货币", example = "CNY")
    private String currency;

    @ApiModelProperty(value = "输入单价")
    private BigDecimal unitPriceIn;

    @ApiModelProperty(value = "输出单价")
    private BigDecimal unitPriceOut;

    @ApiModelProperty(value = "总费用")
    private BigDecimal totalCost;

    @ApiModelProperty(value = "关联类型")
    private String refType;

    @ApiModelProperty(value = "关联ID")
    private Long refId;

    @ApiModelProperty(value = "时间戳(单值)")
    private LocalDateTime ts;

    // 范围查询字段
    @ApiModelProperty(value = "输入Token最小值")
    private Integer tokensInFrom;
    @ApiModelProperty(value = "输入Token最大值")
    private Integer tokensInTo;

    @ApiModelProperty(value = "输出Token最小值")
    private Integer tokensOutFrom;
    @ApiModelProperty(value = "输出Token最大值")
    private Integer tokensOutTo;

    @ApiModelProperty(value = "输入单价下限")
    private BigDecimal unitPriceInFrom;
    @ApiModelProperty(value = "输入单价上限")
    private BigDecimal unitPriceInTo;

    @ApiModelProperty(value = "输出单价下限")
    private BigDecimal unitPriceOutFrom;
    @ApiModelProperty(value = "输出单价上限")
    private BigDecimal unitPriceOutTo;

    @ApiModelProperty(value = "总费用下限")
    private BigDecimal totalCostFrom;
    @ApiModelProperty(value = "总费用上限")
    private BigDecimal totalCostTo;

    @ApiModelProperty(value = "起始时间")
    private LocalDateTime tsFrom;
    @ApiModelProperty(value = "结束时间")
    private LocalDateTime tsTo;

    public CostRecordsQueryDTO() {
        this.setOrderBy("ts");
        this.setSort("desc");
    }
}
