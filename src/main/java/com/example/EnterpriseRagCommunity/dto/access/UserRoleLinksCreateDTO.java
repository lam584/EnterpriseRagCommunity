package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserRoleLinksCreateDTO {
    @ApiModelProperty("用户ID")
    @NotNull
    private Long userId;

    @ApiModelProperty("角色ID")
    @NotNull
    private Long roleId;
}

