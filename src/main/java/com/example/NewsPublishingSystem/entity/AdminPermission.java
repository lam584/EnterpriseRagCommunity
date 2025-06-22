package com.example.NewsPublishingSystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// ======================================================
// 1. AdminPermission
// ======================================================
@Entity
@Table(name = "admin_permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 手动添加缺失的getter和setter方法
    @Column(name = "roles", nullable = false, unique = true)
    private String roles;

    @Column(name = "can_login", nullable = false)
    private boolean canLogin;

    @Column(name = "can_manage_announcement", nullable = false)
    private boolean canManageAnnouncement;

    @Column(name = "can_manage_help_articles", nullable = false)
    private boolean canManageHelpArticles;

    @Column(name = "can_create_super_admin", nullable = false)
    private boolean canCreateSuperAdmin;

    @Column(name = "can_create_admin", nullable = false)
    private boolean canCreateAdmin;

    @Column(name = "can_create_user_account", nullable = false)
    private boolean canCreateUserAccount;

    @Column(name = "can_manage_admin_permissions", nullable = false)
    private boolean canManageAdminPermissions;

    @Column(name = "can_manage_user_permissions", nullable = false)
    private boolean canManageUserPermissions;

    @Column(name = "can_reset_admin_password", nullable = false)
    private boolean canResetAdminPassword;

    @Column(name = "can_reset_user_password", nullable = false)
    private boolean canResetUserPassword;

    @Column(name = "can_pay_user_overdue", nullable = false)
    private boolean canPayUserOverdue;

    @Column(name = "allow_edit_user_profile", nullable = false)
    private boolean allowEditUserProfile;

    @Column(name = "allow_edit_profile", nullable = false)
    private boolean allowEditProfile;

    @Column(name = "allow_edit_other_admin_profile", nullable = false)
    private boolean allowEditOtherAdminProfile;

    @Column(name = "can_manage_topics", nullable = false)
    private boolean canManageTopics;

    @Column(name = "can_manage_news", nullable = false)
    private boolean canManageNews;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}