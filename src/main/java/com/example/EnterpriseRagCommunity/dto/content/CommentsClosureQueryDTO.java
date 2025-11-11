package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class CommentsClosureQueryDTO {
    @ApiModelProperty(value = "祖先评论ID", example = "1")
    private Long ancestorId;

    @ApiModelProperty(value = "后代评论ID", example = "5")
    private Long descendantId;

    @ApiModelProperty(value = "深度起始（含）", example = "0")
    private Integer depthFrom;

    @ApiModelProperty(value = "深度结束（含）", example = "5")
    private Integer depthTo;

    @ApiModelProperty(value = "页码，从1开始", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页大小", example = "20")
    private Integer pageSize = 20;

    @ApiModelProperty(value = "排序字段", example = "depth")
    private String sortBy;

    @ApiModelProperty(value = "排序方向 asc/desc", example = "asc")
    private String sortOrder = "asc";
}
