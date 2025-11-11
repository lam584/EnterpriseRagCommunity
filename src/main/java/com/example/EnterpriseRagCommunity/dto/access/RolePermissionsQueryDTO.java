package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class RolePermissionsQueryDTO extends PageRequestDTO {
    @ApiModelProperty("角色ID")
    private Long roleId;

    @ApiModelProperty("权限ID")
    private Long permissionId;

    @ApiModelProperty("是否允许")
    private Boolean allow;
}
