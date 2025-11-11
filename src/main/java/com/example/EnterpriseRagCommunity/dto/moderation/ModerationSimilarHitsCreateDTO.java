package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
public class ModerationSimilarHitsCreateDTO {
    @NotNull
    @ApiModelProperty(value = "内容类型", required = true, example = "POST")
    private ContentType contentType;

    @NotNull
    @ApiModelProperty(value = "内容ID", required = true, example = "123")
    private Long contentId;

    @ApiModelProperty(value = "相似样本ID/参考ID(可选)", required = false, example = "456")
    private Long candidateId;

    @NotNull
    @ApiModelProperty(value = "相似度距离", required = true, example = "0.42")
    private Double distance;

    @NotNull
    @ApiModelProperty(value = "相似度阈值", required = true, example = "0.50")
    private Double threshold;

    @ApiModelProperty(value = "匹配时间(后端填充, 默认当前)", required = false)
    @JsonIgnore // 由数据库默认值或服务端填充
    private LocalDateTime matchedAt;
}

