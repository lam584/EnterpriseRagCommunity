package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.dto.access.PageRequestDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Source;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class RiskLabelingQueryDTO extends PageRequestDTO {
    @ApiModelProperty(value = "记录ID")
    private Long id;

    @ApiModelProperty(value = "目标类型", example = "POST")
    private ContentType targetType;

    @ApiModelProperty(value = "目标ID", example = "1000")
    private Long targetId;

    @ApiModelProperty(value = "风险标签ID", example = "10")
    private Long tagId;

    @ApiModelProperty(value = "来源", example = "LLM")
    private Source source;

    // 单值与范围查询并存
    @ApiModelProperty(value = "置信度（等值）", example = "0.85")
    private BigDecimal confidence;

    @ApiModelProperty(value = "最小置信度", example = "0.5")
    private BigDecimal minConfidence;

    @ApiModelProperty(value = "最大置信度", example = "0.95")
    private BigDecimal maxConfidence;

    @ApiModelProperty(value = "创建时间（等值）", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime createdTo;

    @ApiModelProperty(value = "导出标记，预留")
    private Boolean export = false;
}
