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

    // 手动添加缺失的getter和setter方法
    public void setRoles(String roles) {
        this.roles = roles;
    }

    public void setCanLogin(boolean canLogin) {
        this.canLogin = canLogin;
    }

    public void setCanManageAnnouncement(boolean canManageAnnouncement) {
        this.canManageAnnouncement = canManageAnnouncement;
    }

    public void setCanManageHelpArticles(boolean canManageHelpArticles) {
        this.canManageHelpArticles = canManageHelpArticles;
    }

    public void setCanCreateSuperAdmin(boolean canCreateSuperAdmin) {
        this.canCreateSuperAdmin = canCreateSuperAdmin;
    }

    public void setCanCreateAdmin(boolean canCreateAdmin) {
        this.canCreateAdmin = canCreateAdmin;
    }

    public void setCanCreateUserAccount(boolean canCreateUserAccount) {
        this.canCreateUserAccount = canCreateUserAccount;
    }

    public void setCanManageAdminPermissions(boolean canManageAdminPermissions) {
        this.canManageAdminPermissions = canManageAdminPermissions;
    }

    public void setCanManageUserPermissions(boolean canManageUserPermissions) {
        this.canManageUserPermissions = canManageUserPermissions;
    }

    public void setCanResetAdminPassword(boolean canResetAdminPassword) {
        this.canResetAdminPassword = canResetAdminPassword;
    }

    public void setCanResetUserPassword(boolean canResetUserPassword) {
        this.canResetUserPassword = canResetUserPassword;
    }

    public void setCanPayUserOverdue(boolean canPayUserOverdue) {
        this.canPayUserOverdue = canPayUserOverdue;
    }

    public void setAllowEditProfile(boolean allowEditProfile) {
        this.allowEditProfile = allowEditProfile;
    }

    public void setAllowEditOtherAdminProfile(boolean allowEditOtherAdminProfile) {
        this.allowEditOtherAdminProfile = allowEditOtherAdminProfile;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setAllowEditUserProfile(boolean allowEditUserProfile) {
        this.allowEditUserProfile = allowEditUserProfile;
    }
}