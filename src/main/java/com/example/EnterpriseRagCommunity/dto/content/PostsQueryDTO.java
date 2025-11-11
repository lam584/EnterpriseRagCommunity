package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class PostsQueryDTO {
    @ApiModelProperty(value = "帖子ID", example = "100")
    private Long id;

    @ApiModelProperty(value = "所属租户ID", example = "1")
    private Long tenantId;

    @ApiModelProperty(value = "板块ID", example = "10")
    private Long boardId;

    @ApiModelProperty(value = "作者ID", example = "200")
    private Long authorId;

    // Derivative / cross-table filter originally present; retain but mark as extension
    @ApiModelProperty(value = "标签ID（扩展关联筛选，非 posts 表原生列）", example = "5")
    private Long tagId;

    @ApiModelProperty(value = "帖子状态", example = "PUBLISHED")
    private PostStatus status;

    @ApiModelProperty(value = "内容格式", example = "MARKDOWN")
    private ContentFormat contentFormat;

    @ApiModelProperty(value = "是否已软删除", example = "false")
    private Boolean isDeleted;

    @ApiModelProperty(value = "发布时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime publishedAtFrom;

    @ApiModelProperty(value = "发布时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime publishedAtTo;

    @ApiModelProperty(value = "创建时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAtFrom;

    @ApiModelProperty(value = "创建时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime createdAtTo;

    @ApiModelProperty(value = "更新时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime updatedAtFrom;

    @ApiModelProperty(value = "更新时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime updatedAtTo;

    @ApiModelProperty(value = "标题精确匹配", example = "Site maintenance notice")
    private String title;

    @ApiModelProperty(value = "标题模糊匹配", example = "notice")
    private String titleLike;

    @ApiModelProperty(value = "内容模糊匹配", example = "upgrade")
    private String contentLike;

    @ApiModelProperty(value = "元数据键是否存在", example = "true")
    private String metadataHasKey;

    @ApiModelProperty(value = "元数据原始匹配(JSON 片段字符串)", example = "{\"lang\":\"zh\"}")
    private String metadataContains;

    @ApiModelProperty(value = "原始元数据过滤（完全匹配用）")
    private Map<String, Object> metadata;

    @ApiModelProperty(value = "页码，从1开始", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页大小", example = "20")
    private Integer pageSize = 20;

    @ApiModelProperty(value = "排序字段", example = "publishedAt")
    private String sortBy;

    @ApiModelProperty(value = "排序方向 asc/desc", example = "desc")
    private String sortOrder = "desc";
}
