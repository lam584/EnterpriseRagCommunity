package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class ModerationSimilarHitsUpdateDTO {
    @ApiModelProperty(value = "主键ID", required = true)
    @NotNull
    private Long id;

    @ApiModelProperty(value = "内容类型", required = false, example = "POST")
    private Optional<ContentType> contentType = Optional.empty();

    @ApiModelProperty(value = "内容ID", required = false, example = "456")
    private Optional<Long> contentId = Optional.empty();

    @ApiModelProperty(value = "相似样本/参考ID", required = false)
    private Optional<Long> candidateId = Optional.empty();

    @ApiModelProperty(value = "相似距离", required = false, example = "0.312")
    private Optional<@DecimalMin("0.0") Double> distance = Optional.empty();

    @ApiModelProperty(value = "距离阈值", required = false, example = "0.500")
    private Optional<@DecimalMin("0.0") Double> threshold = Optional.empty();

    @ApiModelProperty(value = "命中时间(只读)")
    @JsonIgnore
    private Optional<LocalDateTime> matchedAt = Optional.empty();
}

