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

    // 原 @EmbeddedId 模式移除，改为显式两个主键字段
    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Id
    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    // 可选：若仍需导航属性，可使用只读关联（遵守外键 Long 字段主权）
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private UserRolesEntity role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", insertable = false, updatable = false)
    private PermissionsEntity permission;

    @Column(name = "allow", nullable = false)
    private Boolean allow;
}
