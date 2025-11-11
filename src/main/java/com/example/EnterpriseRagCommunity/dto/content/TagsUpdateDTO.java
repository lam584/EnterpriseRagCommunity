package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class TagsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "标签ID", required = true, example = "10")
    private Long id;

    @ApiModelProperty(value = "租户ID", example = "1")
    private Optional<Long> tenantId = Optional.empty();

    @ApiModelProperty(value = "标签类型", example = "TOPIC")
    private Optional<TagType> type = Optional.empty();

    @Size(max = 64)
    @ApiModelProperty(value = "标签名称", example = "Java")
    private Optional<String> name = Optional.empty();

    @Size(max = 96)
    @ApiModelProperty(value = "Slug", example = "java")
    private Optional<String> slug = Optional.empty();

    @Size(max = 255)
    @ApiModelProperty(value = "描述", example = "Java related content")
    private Optional<String> description = Optional.empty();

    @ApiModelProperty(value = "是否系统标签", example = "false")
    private Optional<Boolean> isSystem = Optional.empty();

    @ApiModelProperty(value = "是否启用", example = "true")
    private Optional<Boolean> isActive = Optional.empty();

    // 审计字段：仅映射不可修改，可在业务层忽略修改
    @ApiModelProperty(value = "创建时间(只读)", example = "2025-01-01T00:00:00")
    private Optional<LocalDateTime> createdAt = Optional.empty();
}
