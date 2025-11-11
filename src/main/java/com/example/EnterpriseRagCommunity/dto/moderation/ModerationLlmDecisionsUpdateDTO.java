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
import java.util.Optional;

@Data
public class ModerationLlmDecisionsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "内容类型", required = false)
    private Optional<ContentType> contentType = Optional.empty();

    @ApiModelProperty(value = "内容ID", required = false)
    private Optional<Long> contentId = Optional.empty();

    @ApiModelProperty(value = "判定模型", required = false)
    private Optional<@Size(max = 64) @NotBlank String> model = Optional.empty();

    @ApiModelProperty(value = "标签集合(JSON)", required = false)
    private Optional<Map<String, Object>> labels = Optional.empty();

    @ApiModelProperty(value = "综合置信度", required = false)
    private Optional<@Digits(integer = 1, fraction = 4) @DecimalMin("0.0000") @DecimalMax("9.9999") BigDecimal> confidence = Optional.empty();

    @ApiModelProperty(value = "裁决结果", required = false)
    private Optional<Verdict> verdict = Optional.empty();

    @ApiModelProperty(value = "提示词模板ID", required = false)
    private Optional<Long> promptId = Optional.empty();

    @ApiModelProperty(value = "输入Token", required = false)
    private Optional<Integer> tokensIn = Optional.empty();

    @ApiModelProperty(value = "输出Token", required = false)
    private Optional<Integer> tokensOut = Optional.empty();

    // created/decidedAt should not be updated generally; still mapped
    @ApiModelProperty(value = "判定时间(只读)")
    @JsonIgnore
    private Optional<LocalDateTime> decidedAt = Optional.empty();
}
