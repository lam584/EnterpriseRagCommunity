package com.example.EnterpriseRagCommunity.dto.access;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RolePermissionUpsertDTO {
    /**
     * 角色ID：
     * - 编辑/覆盖更新（/role/{roleId}）时由 path 参数决定，body 中可不传
     * - 新建角色（自动分配 roleId）时也可不传
     */
    private Long roleId;

    /** 角色名（可选） */
    private String roleName;

    @NotNull
    private Long permissionId;

    /**
     * true = allow, false = deny
     */
    @NotNull
    private Boolean allow;
}
