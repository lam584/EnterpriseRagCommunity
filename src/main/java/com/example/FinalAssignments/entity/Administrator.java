package com.example.FinalAssignments.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "administrators")
public class Administrator {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 账号 */
    @Column(nullable = false, unique = true, length = 100)
    private String account;

    /** 密码 */
    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 100)
    private String phone;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String sex;

    /** 注册日期 */
    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    /** 外键到 admin_permissions */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permissions_id", nullable = false)
    private AdminPermission permission;

    /** 是否激活 */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}