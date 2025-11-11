package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RolePermissionsCreateDTO {
    @ApiModelProperty("角色ID")
    @NotNull
    private Long roleId;

    @ApiModelProperty("权限ID")
    @NotNull
    private Long permissionId;

    @ApiModelProperty("是否允许")
    @NotNull
    private Boolean allow;
}
