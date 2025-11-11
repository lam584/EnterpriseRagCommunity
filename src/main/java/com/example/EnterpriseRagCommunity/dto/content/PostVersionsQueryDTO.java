package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostVersionsQueryDTO {
    @ApiModelProperty(value = "主键ID", example = "123")
    private Long id;

    @ApiModelProperty(value = "帖子ID", example = "1000")
    private Long postId;

    @ApiModelProperty(value = "编辑人ID", example = "2")
    private Long editorId;

    @ApiModelProperty(value = "版本号", example = "3")
    private Integer version;

    @ApiModelProperty(value = "版本号最小值", example = "1")
    private Integer versionMin;

    @ApiModelProperty(value = "版本号最大值", example = "10")
    private Integer versionMax;

    @ApiModelProperty(value = "标题（模糊匹配用）", example = "更新后的标题")
    private String title;

    @ApiModelProperty(value = "内容（模糊匹配用）", example = "内容片段")
    private String content;

    @ApiModelProperty(value = "编辑原因（模糊匹配用）", example = "修正错别字")
    private String reason;

    @ApiModelProperty(value = "创建时间", example = "2025-01-01T12:00:00")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAtFrom;

    @ApiModelProperty(value = "创建时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime createdAtTo;

    @ApiModelProperty(value = "页码，从1开始", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页大小", example = "20")
    private Integer pageSize = 20;

    @ApiModelProperty(value = "排序字段", example = "version")
    private String sortBy;

    @ApiModelProperty(value = "排序方向 asc/desc", example = "desc")
    private String sortOrder = "desc";
}
