package com.example.EnterpriseRagCommunity.entity.access;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "role_permissions")
@IdClass(RolePermissionId.class)
public class RolePermissionsEntity {

    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "role_name")
    private String roleName;

    @Id
    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    // 说明：roleId 不再关联到角色定义表（user_roles 已删除）。

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", insertable = false, updatable = false)
    private PermissionsEntity permission;

    @Column(name = "allow", nullable = false)
    private Boolean allow;
}
