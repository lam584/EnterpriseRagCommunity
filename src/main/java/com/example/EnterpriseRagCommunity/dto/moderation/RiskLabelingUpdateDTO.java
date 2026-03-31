package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Source;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RiskLabelingUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "标注记录ID", required = true, example = "1000")
    private Long id;

    // 为了显式映射但不允许修改，可只读或忽略
    @ApiModelProperty(value = "目标类型（只读）", example = "POST")
    @JsonIgnore
    private ContentType targetType;

    @ApiModelProperty(value = "目标ID（只读）", example = "1000")
    @JsonIgnore
    private Long targetId;

    @ApiModelProperty(value = "风险标签ID（只读）", example = "10")
    @JsonIgnore
    private Long tagId;

    @ApiModelProperty(value = "来源（可选）", example = "HUMAN")
    private Source source;

    @ApiModelProperty(value = "置信度（0-1，可选）", example = "0.85")
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Digits(integer = 1, fraction = 4)
    private BigDecimal confidence;

    @ApiModelProperty(value = "创建时间（只读）", example = "2025-01-01T00:00:00")
    @JsonIgnore
    private LocalDateTime createdAt;
}
