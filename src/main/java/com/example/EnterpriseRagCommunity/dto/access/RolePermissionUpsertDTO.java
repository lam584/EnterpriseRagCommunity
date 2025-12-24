package com.example.EnterpriseRagCommunity.dto.access;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RolePermissionUpsertDTO {
    @NotNull
    private Long roleId;

    @NotNull
    private Long permissionId;

    /**
     * true = allow, false = deny
     */
    @NotNull
    private Boolean allow;
}

