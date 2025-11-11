package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Optional;

@Data
public class CommentsClosureUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "祖先评论ID（复合主键）", required = true, example = "1")
    private Long ancestorId;

    @NotNull
    @ApiModelProperty(value = "后代评论ID（复合主键）", required = true, example = "5")
    private Long descendantId;

    @ApiModelProperty(value = "深度（自身为0）")
    private Optional<Integer> depth = Optional.empty();
}
