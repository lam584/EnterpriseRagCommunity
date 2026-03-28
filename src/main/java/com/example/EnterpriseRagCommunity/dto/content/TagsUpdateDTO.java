package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TagsUpdateDTO {
    @ApiModelProperty(value = "标签ID", required = true, example = "10")
    private Long id;

    @ApiModelProperty(value = "租户ID", example = "1")
    private Long tenantId;

    @ApiModelProperty(value = "标签类型", example = "TOPIC")
    private TagType type;

    @Size(max = 64)
    @ApiModelProperty(value = "标签名称", example = "Java")
    private String name;

    @Size(max = 96)
    @ApiModelProperty(value = "Slug", example = "java")
    private String slug;

    @Size(max = 255)
    @ApiModelProperty(value = "描述", example = "Java related content")
    private String description;

    @ApiModelProperty(value = "是否系统标签", example = "false")
    private Boolean isSystem;

    @ApiModelProperty(value = "是否启用", example = "true")
    private Boolean isActive;

    @ApiModelProperty(value = "风险阈值", example = "0.5")
    private Double threshold;

    // 审计字段：仅映射不可修改，可在业务层忽略修改
    @ApiModelProperty(value = "创建时间(只读)", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;
}
