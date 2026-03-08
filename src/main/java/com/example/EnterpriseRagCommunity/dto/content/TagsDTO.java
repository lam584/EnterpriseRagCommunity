package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签返回 DTO。
 *
 * 说明：为了避免直接返回 Entity（Hibernate proxy/字段泄漏风险），API 统一返回 DTO。
 */
@Data
public class TagsDTO {

    @ApiModelProperty(value = "标签ID", example = "10")
    private Long id;

    @ApiModelProperty(value = "所属租户ID", example = "1")
    private Long tenantId;

    @ApiModelProperty(value = "标签类型", example = "TOPIC")
    private TagType type;

    @ApiModelProperty(value = "标签名称", example = "Java")
    private String name;

    @ApiModelProperty(value = "Slug", example = "java")
    private String slug;

    @ApiModelProperty(value = "描述", example = "Java related content")
    private String description;

    @ApiModelProperty(value = "是否系统标签", example = "false")
    private Boolean isSystem;

    @ApiModelProperty(value = "是否启用", example = "true")
    private Boolean isActive;

    @ApiModelProperty(value = "风险阈值", example = "0.5")
    private Double threshold;

    @ApiModelProperty(value = "创建时间", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "使用量（被多少帖子引用）", example = "12")
    private Long usageCount;
}

