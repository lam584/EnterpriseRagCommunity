package com.example.NewsPublishingSystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// ======================================================
// 2. Administrator
// ======================================================
@Entity
@Table(name = "administrators")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Administrator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String account;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String sex;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permissions_id", nullable = false)
    private AdminPermission permission;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 添加getUsername方法，用于向后兼容
    public String getUsername() {
        return this.account;
    }

    // 添加setIsActive方法，用于向后兼容
    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }

    // 添加getPermissionsId方法，用于DTO转换
    public Long getPermissionsId() {
        if (this.permission != null) {
            return this.permission.getId();
        }
        return null;
    }

    // 手动添加缺失的setter方法
    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public void setPermission(AdminPermission permission) {
        this.permission = permission;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
