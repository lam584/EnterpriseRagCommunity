package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
public class ModerationRuleHitsCreateDTO {
    @NotNull
    @ApiModelProperty(value = "内容类型", required = true, example = "POST")
    private ContentType contentType;

    @NotNull
    @ApiModelProperty(value = "内容ID", required = true, example = "123")
    private Long contentId;

    @NotNull
    @ApiModelProperty(value = "命中规则ID", required = true, example = "1")
    private Long ruleId;

    @Size(max = 255)
    @ApiModelProperty(value = "命中文本片段", required = false)
    private String snippet;

    @NotNull
    @ApiModelProperty(value = "命中时间(后端填充, 默认当前)", required = false)
    @JsonIgnore // DB 默认值或服务层填充
    private LocalDateTime matchedAt;
}

