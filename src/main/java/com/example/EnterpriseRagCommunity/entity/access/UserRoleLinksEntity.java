package com.example.EnterpriseRagCommunity.entity.access;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

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
        private String scopeType;
        private Long scopeId;
    }

    // 显式映射复合主键字段，与 SQL 列一一对应
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Id
    @Column(name = "scope_type", nullable = false, length = 16)
    private String scopeType = "GLOBAL";

    @Id
    @Column(name = "scope_id", nullable = false)
    private Long scopeId = 0L;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "assigned_reason")
    private String assignedReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (scopeType == null || scopeType.isBlank()) {
            scopeType = "GLOBAL";
        }
        if (scopeId == null) {
            scopeId = 0L;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

