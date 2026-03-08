package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TagsCreateDTO {
    @ApiModelProperty(value = "所属租户ID，可选", example = "1")
    private Long tenantId;

    @NotNull
    @ApiModelProperty(value = "标签类型", required = true, example = "TOPIC")
    private TagType type;

    @NotBlank
    @Size(max = 64)
    @ApiModelProperty(value = "标签名称", required = true, example = "Java")
    private String name;

    @NotBlank
    @Size(max = 96)
    @ApiModelProperty(value = "Slug", required = true, example = "java")
    private String slug;

    @Size(max = 255)
    @ApiModelProperty(value = "描述", example = "Java related content")
    private String description;

    @NotNull
    @ApiModelProperty(value = "是否系统标签", required = true, example = "false")
    private Boolean isSystem;

    @NotNull
    @ApiModelProperty(value = "是否启用", required = true, example = "true")
    private Boolean isActive;

    @ApiModelProperty(value = "风险阈值", example = "0.5")
    private Double threshold;

    // 显式映射审计字段（由系统填写），前端可不传
    @ApiModelProperty(value = "创建时间(系统填充)", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;
}
