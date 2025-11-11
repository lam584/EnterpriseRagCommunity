package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
@EqualsAndHashCode(callSuper = true)
@Data
public class ModerationLlmDecisionsQueryDTO extends PageRequestDTO {
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "内容类型", example = "POST")
    private ContentType contentType;

    @ApiModelProperty(value = "内容ID", example = "123")
    private Long contentId;

    @ApiModelProperty(value = "模型名(模糊)", example = "gpt-4o-mini")
    private String model;

    @ApiModelProperty(value = "标签集合(JSON)")
    private Map<String, Object> labels;

    @ApiModelProperty(value = "审核结论", example = "REJECT")
    private Verdict verdict;

    @ApiModelProperty(value = "置信度(精确)", example = "0.8123")
    private BigDecimal confidence;

    @ApiModelProperty(value = "最小置信度", example = "0.5")
    private BigDecimal minConfidence;

    @ApiModelProperty(value = "最大置信度", example = "0.95")
    private BigDecimal maxConfidence;

    @ApiModelProperty(value = "提示词模板ID")
    private Long promptId;

    @ApiModelProperty(value = "输入Token")
    private Integer tokensIn;

    @ApiModelProperty(value = "输入Token最小")
    private Integer tokensInMin;

    @ApiModelProperty(value = "输入Token最大")
    private Integer tokensInMax;

    @ApiModelProperty(value = "输出Token")
    private Integer tokensOut;

    @ApiModelProperty(value = "输出Token最小")
    private Integer tokensOutMin;

    @ApiModelProperty(value = "输出Token最大")
    private Integer tokensOutMax;

    @ApiModelProperty(value = "判定时间(精确)")
    private LocalDateTime decidedAt;

    @ApiModelProperty(value = "决策时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime decidedFrom;

    @ApiModelProperty(value = "决策时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime decidedTo;
}
