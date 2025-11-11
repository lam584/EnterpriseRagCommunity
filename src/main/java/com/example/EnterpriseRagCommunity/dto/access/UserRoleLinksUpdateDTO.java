package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserRoleLinksUpdateDTO {
    @ApiModelProperty("用户ID（复合主键）")
    @NotNull
    private Long userId;

    @ApiModelProperty("角色ID（复合主键）")
    @NotNull
    private Long roleId;
}

