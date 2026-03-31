package com.example.EnterpriseRagCommunity.dto.content;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
public class BoardsUpdateDTO {
    @NotNull
    @ApiModelProperty(value = "板块ID", required = true, example = "100")
    private Long id;

    @ApiModelProperty(value = "租户ID", example = "1")
    private Long tenantId;
    @JsonIgnore
    private boolean hasTenantId;

    @ApiModelProperty(value = "父级板块ID，为空表示顶级", example = "0")
    private Long parentId;
    @JsonIgnore
    private boolean hasParentId;

    @Size(max = 64)
    @ApiModelProperty(value = "板块名称", example = "Announcements")
    private String name;
    @JsonIgnore
    private boolean hasName;

    @Size(max = 255)
    @ApiModelProperty(value = "板块描述", example = "Official announcements and updates")
    private String description;
    @JsonIgnore
    private boolean hasDescription;

    @ApiModelProperty(value = "是否可见", example = "true")
    private Boolean visible;
    @JsonIgnore
    private boolean hasVisible;

    @ApiModelProperty(value = "排序值（越小越靠前）", example = "10")
    private Integer sortOrder;
    @JsonIgnore
    private boolean hasSortOrder;

    // Audit fields present but ignored for update (createdAt immutable, updatedAt system managed)
    @JsonIgnore
    @ApiModelProperty(value = "创建时间(只读)")
    private LocalDateTime createdAt;

    @JsonIgnore
    @ApiModelProperty(value = "更新时间(系统生成)")
    private LocalDateTime updatedAt;

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
        this.hasTenantId = true;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
        this.hasParentId = true;
    }

    public void setName(String name) {
        this.name = name;
        this.hasName = true;
    }

    public void setDescription(String description) {
        this.description = description;
        this.hasDescription = true;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
        this.hasVisible = true;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
        this.hasSortOrder = true;
    }
}
