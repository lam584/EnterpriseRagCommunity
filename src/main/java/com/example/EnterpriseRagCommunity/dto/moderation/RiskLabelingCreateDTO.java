package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Source;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RiskLabelingCreateDTO {
    // id 由数据库生成，不在 CreateDTO 中出现

    @NotNull
    @ApiModelProperty(value = "目标类型", required = true, example = "POST")
    private ContentType targetType;

    @NotNull
    @ApiModelProperty(value = "目标ID", required = true, example = "1000")
    private Long targetId;

    @NotNull
    @ApiModelProperty(value = "风险标签ID", required = true, example = "10")
    private Long tagId;

    @NotNull
    @ApiModelProperty(value = "来源", required = true, example = "LLM")
    private Source source;

    @ApiModelProperty(value = "置信度 (0-1，可选)", example = "0.85")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    @Digits(integer = 1, fraction = 4)
    private BigDecimal confidence;

    // 审计字段必须显式映射，但可由系统填充
    @ApiModelProperty(value = "创建时间（系统填充）", example = "2025-01-01T00:00:00")
    @JsonIgnore // 前端不需要传递
    private LocalDateTime createdAt;
}

