package com.example.EnterpriseRagCommunity.entity.access;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class RolePermissionId implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long roleId;
    private Long permissionId;
}

