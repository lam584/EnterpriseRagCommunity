package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FavoritesQueryDTO {
    @ApiModelProperty(value = "主键ID", example = "1")
    private Long id;

    @ApiModelProperty(value = "用户ID", example = "200")
    private Long userId;

    @ApiModelProperty(value = "帖子ID", example = "1000")
    private Long postId;

    @ApiModelProperty(value = "收藏时间精确等值查询", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "收藏时间范围起 (>=)", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAtFrom;

    @ApiModelProperty(value = "收藏时间范围止 (<=)", example = "2025-12-31T23:59:59")
    private LocalDateTime createdAtTo;

    @ApiModelProperty(value = "页码，从1开始", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页大小", example = "20")
    private Integer pageSize = 20;

    @ApiModelProperty(value = "排序字段", example = "createdAt")
    private String sortBy;

    @ApiModelProperty(value = "排序方向 asc/desc", example = "desc")
    private String sortOrder = "desc";
}
