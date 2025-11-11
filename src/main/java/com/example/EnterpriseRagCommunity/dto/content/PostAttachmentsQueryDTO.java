package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostAttachmentsQueryDTO {
    // Table columns (single value filters)
    @ApiModelProperty(value = "主键ID", example = "1")
    private Long id;

    @ApiModelProperty(value = "帖子ID", example = "1000")
    private Long postId;

    @ApiModelProperty(value = "附件访问URL", example = "https://cdn.example.com/a.png")
    private String url;

    @ApiModelProperty(value = "原始文件名", example = "a.png")
    private String fileName;

    @ApiModelProperty(value = "文件名模糊匹配", example = "image")
    private String fileNameLike;

    @ApiModelProperty(value = "MIME类型", example = "image/png")
    private String mimeType;

    @ApiModelProperty(value = "文件大小（字节）", example = "2048")
    private Long sizeBytes;

    // Range filters (size)
    @ApiModelProperty(value = "大小下限（字节）", example = "0")
    private Long sizeBytesFrom;

    @ApiModelProperty(value = "大小上限（字节）", example = "1048576")
    private Long sizeBytesTo;

    @ApiModelProperty(value = "图片宽（像素）", example = "800")
    private Integer width;

    @ApiModelProperty(value = "图片宽起", example = "0")
    private Integer widthFrom;

    @ApiModelProperty(value = "图片宽止", example = "1920")
    private Integer widthTo;

    @ApiModelProperty(value = "图片高（像素）", example = "600")
    private Integer height;

    @ApiModelProperty(value = "图片高起", example = "0")
    private Integer heightFrom;

    @ApiModelProperty(value = "图片高止", example = "1080")
    private Integer heightTo;

    @ApiModelProperty(value = "创建时间", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime createdTo;

    // Pagination & sorting (non-table control fields)
    @ApiModelProperty(value = "页码，从1开始", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页大小", example = "20")
    private Integer pageSize = 20;

    @ApiModelProperty(value = "排序字段", example = "createdAt")
    private String sortBy;

    @ApiModelProperty(value = "排序方向 asc/desc", example = "desc")
    private String sortOrder = "desc";
}
