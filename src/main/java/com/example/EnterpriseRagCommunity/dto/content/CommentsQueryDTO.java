package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentsQueryDTO {
    @ApiModelProperty(value = "评论ID", example = "1")
    private Long id;

    @ApiModelProperty(value = "帖子ID", example = "1000")
    private Long postId;

    @ApiModelProperty(value = "父评论ID", example = "0")
    private Long parentId;

    @ApiModelProperty(value = "作者ID", example = "200")
    private Long authorId;

    @ApiModelProperty(value = "评论状态", example = "VISIBLE")
    private CommentStatus status;

    @ApiModelProperty(value = "是否删除(等值查询)", example = "false")
    private Boolean isDeleted;

    @ApiModelProperty(value = "是否包含软删除数据，默认否(扩展开关)", example = "false")
    private Boolean includeDeleted = false;

    @ApiModelProperty(value = "内容等值匹配", example = "thanks")
    private String content;

    @ApiModelProperty(value = "内容模糊匹配", example = "thanks")
    private String contentLike;

    @ApiModelProperty(value = "创建时间(等值)", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime createdTo;

    @ApiModelProperty(value = "更新时间(等值)", example = "2025-01-01T00:00:00")
    private LocalDateTime updatedAt;

    @ApiModelProperty(value = "更新时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime updatedFrom;

    @ApiModelProperty(value = "更新时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime updatedTo;

    @ApiModelProperty(value = "页码，从1开始", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页大小", example = "20")
    private Integer pageSize = 20;

    @ApiModelProperty(value = "排序字段", example = "createdAt")
    private String sortBy;

    @ApiModelProperty(value = "排序方向 asc/desc", example = "asc")
    private String sortOrder = "asc";
}
