package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Optional;

@Data
public class RolePermissionsUpdateDTO {
    @ApiModelProperty("角色ID（复合主键）")
    @NotNull
    private Long roleId;

    @ApiModelProperty("权限ID（复合主键）")
    @NotNull
    private Long permissionId;

    @ApiModelProperty("是否允许")
    private Optional<Boolean> allow;
}
