package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class ModerationRuleHitsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "命中记录ID", required = true, example = "1")
    private Long id;

    @ApiModelProperty(value = "内容类型(可选)")
    private Optional<ContentType> contentType = Optional.empty();

    @ApiModelProperty(value = "内容ID(可选)")
    private Optional<Long> contentId = Optional.empty();

    @ApiModelProperty(value = "命中规则ID(可选)")
    private Optional<Long> ruleId = Optional.empty();

    @Size(max = 255)
    @ApiModelProperty(value = "命中文本片段(可选)")
    private Optional<String> snippet = Optional.empty();

    @ApiModelProperty(value = "命中时间(不可由外部修改)")
    @JsonIgnore
    private Optional<LocalDateTime> matchedAt = Optional.empty();
}

