package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Data
public class BoardsCreateDTO {
    @ApiModelProperty(value = "所属租户ID，可选", example = "1")
    private Long tenantId;

    @ApiModelProperty(value = "父级板块ID，为空表示顶级", example = "0")
    private Long parentId;

    @NotBlank
    @Size(max = 64)
    @ApiModelProperty(value = "板块名称", required = true, example = "Announcements")
    private String name;

    @Size(max = 255)
    @ApiModelProperty(value = "板块描述", example = "Official announcements and updates")
    private String description;

    @NotNull
    @ApiModelProperty(value = "是否可见", required = true, example = "true")
    private Boolean visible;

    @NotNull
    @ApiModelProperty(value = "排序值（越小越靠前）", required = true, example = "10")
    private Integer sortOrder;

    // Audit fields explicitly mapped but ignored on create input
    @JsonIgnore
    @ApiModelProperty(value = "创建时间(系统生成)", example = "2025-01-01T00:00:00")
    private LocalDateTime createdAt;

    @JsonIgnore
    @ApiModelProperty(value = "更新时间(系统生成)", example = "2025-01-01T00:00:00")
    private LocalDateTime updatedAt;
}
