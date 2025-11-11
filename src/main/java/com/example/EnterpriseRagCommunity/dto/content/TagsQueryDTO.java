package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TagsQueryDTO {
    @ApiModelProperty(value = "标签ID", example = "10")
    private Long id;

    @ApiModelProperty(value = "所属租户ID", example = "1")
    private Long tenantId;

    @ApiModelProperty(value = "标签类型", example = "TOPIC")
    private TagType type;

    @ApiModelProperty(value = "标签名称精确匹配", example = "Java")
    private String name;

    @ApiModelProperty(value = "名称模糊匹配", example = "java")
    private String nameLike;

    @ApiModelProperty(value = "Slug 精确匹配", example = "java")
    private String slug;

    @ApiModelProperty(value = "描述精确匹配", example = "Java related content")
    private String description;

    @ApiModelProperty(value = "是否系统标签", example = "false")
    private Boolean isSystem;

    @ApiModelProperty(value = "是否启用", example = "true")
    private Boolean isActive;

    @ApiModelProperty(value = "创建时间精确", example = "2025-01-01T10:00:00")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "创建时间起", example = "2025-01-01T00:00:00")
    private LocalDateTime createdFrom;

    @ApiModelProperty(value = "创建时间止", example = "2025-12-31T23:59:59")
    private LocalDateTime createdTo;

    @ApiModelProperty(value = "页码，从1开始", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页大小", example = "20")
    private Integer pageSize = 20;

    @ApiModelProperty(value = "排序字段", example = "createdAt")
    private String sortBy;

    @ApiModelProperty(value = "排序方向 asc/desc", example = "desc")
    private String sortOrder = "desc";
}
