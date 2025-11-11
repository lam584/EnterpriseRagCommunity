package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BoardsQueryDTO {
    @ApiModelProperty(value = "板块ID", example = "100")
    private Long id;

    @ApiModelProperty(value = "所属租户ID", example = "1")
    private Long tenantId;

    @ApiModelProperty(value = "父级板块ID，为空表示顶级", example = "0")
    private Long parentId;

    @ApiModelProperty(value = "名称精确匹配", example = "Announcements")
    private String name;

    @ApiModelProperty(value = "名称模糊匹配", example = "ann")
    private String nameLike;

    @ApiModelProperty(value = "描述精确匹配", example = "Official announcements and updates")
    private String description;

    @ApiModelProperty(value = "是否可见", example = "true")
    private Boolean visible;

    @ApiModelProperty(value = "排序值精确匹配", example = "10")
    private Integer sortOrder;

    @ApiModelProperty(value = "排序值下界", example = "0")
    private Integer sortOrderFrom;

    @ApiModelProperty(value = "排序值上界", example = "100")
    private Integer sortOrderTo;

    @ApiModelProperty(value = "创建时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime createdTo;

    @ApiModelProperty(value = "更新时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime updatedFrom;

    @ApiModelProperty(value = "更新时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime updatedTo;

    @ApiModelProperty(value = "页码，从1开始", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页大小", example = "20")
    private Integer pageSize = 20;

    @ApiModelProperty(value = "排序字段", example = "sortOrder")
    private String sortBy;

    @ApiModelProperty(value = "排序方向 asc/desc", example = "asc")
    private String sortOrderDirection = "asc";
}
