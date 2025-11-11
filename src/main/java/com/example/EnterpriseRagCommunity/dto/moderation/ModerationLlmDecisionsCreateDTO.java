package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ModerationLlmDecisionsCreateDTO {
    // id excluded (auto generated)
    @ApiModelProperty(value = "内容类型", required = true, example = "POST")
    @NotNull
    private ContentType contentType;

    @ApiModelProperty(value = "内容ID", required = true, example = "123")
    @NotNull
    private Long contentId;

    @ApiModelProperty(value = "判定模型", required = true, example = "gpt-4o-mini")
    @NotBlank
    @Size(max = 64)
    private String model;

    @ApiModelProperty(value = "标签集合(JSON)", required = true)
    @NotNull
    private Map<String, Object> labels;

    @ApiModelProperty(value = "综合置信度", required = true, example = "0.8123")
    @NotNull
    @Digits(integer = 1, fraction = 4)
    @DecimalMin("0.0000")
    @DecimalMax("9.9999")
    private BigDecimal confidence;

    @ApiModelProperty(value = "裁决结果", required = true, example = "APPROVE")
    @NotNull
    private Verdict verdict;

    @ApiModelProperty(value = "提示词模板ID", required = false)
    private Long promptId;

    @ApiModelProperty(value = "输入Token", required = false)
    private Integer tokensIn;

    @ApiModelProperty(value = "输出Token", required = false)
    private Integer tokensOut;

    @ApiModelProperty(value = "判定时间(数据库默认生成)", required = false)
    @JsonIgnore
    private LocalDateTime decidedAt;
}
