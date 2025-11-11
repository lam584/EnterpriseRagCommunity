package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReactionsCreateDTO {
    @NotNull
    @ApiModelProperty(value = "用户ID", required = true, example = "200")
    private Long userId;

    @NotNull
    @ApiModelProperty(value = "目标类型", required = true, example = "POST")
    private ReactionTargetType targetType;

    @NotNull
    @ApiModelProperty(value = "目标ID", required = true, example = "1000")
    private Long targetId;

    @NotNull
    @ApiModelProperty(value = "互动类型", required = true, example = "LIKE")
    private ReactionType type;

    @JsonIgnore
    @ApiModelProperty(value = "创建时间，由数据库默认值填充")
    private LocalDateTime createdAt;
}

