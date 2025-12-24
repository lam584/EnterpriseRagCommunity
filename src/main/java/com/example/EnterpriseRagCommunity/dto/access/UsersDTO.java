package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * Safe user DTO for returning to frontend (does NOT include passwordHash).
 * Used by auth "current-admin" and login responses.
 */
@Data
public class UsersDTO {

    @ApiModelProperty("用户ID")
    private Long id;

    @ApiModelProperty("所属租户ID")
    private Long tenantId;

    @ApiModelProperty("邮箱")
    private String email;

    @ApiModelProperty("用户名")
    private String username;

    @ApiModelProperty("账户状态")
    private AccountStatus status;

    @ApiModelProperty("是否删除（软删除）")
    private Boolean isDeleted;

    @ApiModelProperty("扩展元数据(JSON)")
    private Map<String, Object> metadata;
}

