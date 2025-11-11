package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserRoleLinksQueryDTO extends PageRequestDTO {
    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("角色ID")
    private Long roleId;
}
