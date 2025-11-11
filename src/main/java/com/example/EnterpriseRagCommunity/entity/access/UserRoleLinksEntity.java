package com.example.EnterpriseRagCommunity.entity.access;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@Entity
@Table(name = "user_role_links")
@IdClass(UserRoleLinksEntity.UserRoleLinksPk.class)
public class UserRoleLinksEntity {

    @Data
    @NoArgsConstructor
    public static class UserRoleLinksPk implements Serializable {
        private Long userId;
        private Long roleId;
    }

    // 显式映射复合主键字段，与 SQL 列一一对应
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;
}
