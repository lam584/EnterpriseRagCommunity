package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class BoardsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "板块ID", required = true, example = "100")
    private Long id;

    @ApiModelProperty(value = "租户ID", example = "1")
    private Optional<Long> tenantId = Optional.empty();

    @ApiModelProperty(value = "父级板块ID，为空表示顶级", example = "0")
    private Optional<Long> parentId = Optional.empty();

    @Size(max = 64)
    @ApiModelProperty(value = "板块名称", example = "Announcements")
    private Optional<String> name = Optional.empty();

    @Size(max = 255)
    @ApiModelProperty(value = "板块描述", example = "Official announcements and updates")
    private Optional<String> description = Optional.empty();

    @ApiModelProperty(value = "是否可见", example = "true")
    private Optional<Boolean> visible = Optional.empty();

    @ApiModelProperty(value = "排序值（越小越靠前）", example = "10")
    private Optional<Integer> sortOrder = Optional.empty();

    // Audit fields present but ignored for update (createdAt immutable, updatedAt system managed)
    @JsonIgnore
    @ApiModelProperty(value = "创建时间(只读)")
    private LocalDateTime createdAt;

    @JsonIgnore
    @ApiModelProperty(value = "更新时间(系统生成)")
    private LocalDateTime updatedAt;
}
