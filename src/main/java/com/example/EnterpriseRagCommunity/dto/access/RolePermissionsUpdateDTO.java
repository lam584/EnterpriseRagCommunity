package com.example.EnterpriseRagCommunity.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RolePermissionsUpdateDTO {
    @ApiModelProperty("角色ID（复合主键）")
    @NotNull
    private Long roleId;

    @ApiModelProperty("权限ID（复合主键）")
    @NotNull
    private Long permissionId;

    @ApiModelProperty("是否允许")
    private Boolean allow;
    @JsonIgnore
    private boolean hasAllow;

    public void setAllow(Boolean allow) {
        this.allow = allow;
        this.hasAllow = true;
    }
}
