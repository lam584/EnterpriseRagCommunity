package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentsClosureCreateDTO {
    @NotNull
    @ApiModelProperty(value = "祖先评论ID", required = true, example = "1")
    private Long ancestorId;

    @NotNull
    @ApiModelProperty(value = "后代评论ID（含自身）", required = true, example = "5")
    private Long descendantId;

    @NotNull
    @ApiModelProperty(value = "深度（自身为0）", required = true, example = "0")
    private Integer depth;
}

