package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class ReactionsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "主键ID", required = true, example = "1")
    private Long id;

    @ApiModelProperty(value = "用户ID", example = "200")
    private Optional<Long> userId = Optional.empty();

    @ApiModelProperty(value = "目标类型", example = "POST")
    private Optional<ReactionTargetType> targetType = Optional.empty();

    @ApiModelProperty(value = "目标ID", example = "1000")
    private Optional<Long> targetId = Optional.empty();

    @ApiModelProperty(value = "互动类型", example = "LIKE")
    private Optional<ReactionType> type = Optional.empty();

    @JsonIgnore
    @ApiModelProperty(value = "创建时间（不可修改）")
    private Optional<LocalDateTime> createdAt = Optional.empty();
}
