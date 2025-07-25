// 文件：java/com/example/NewsHubCommunity/dto/AdminPermissionDTOs.java
package com.example.NewsHubCommunity.dto;

import com.example.NewsHubCommunity.entity.AdminPermission;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 完整的管理员权限 DTO 集合
 */
public class AdminPermissionDTOs {

    /**
     * 用于展示／查询的 DTO
     */
    @Data
    public static class AdminPermissionDTO {
        private Long id;
        private String roles;
        private boolean canLogin;
        private boolean canManageAnnouncement;
        private boolean canManageHelpArticles;
        private boolean canCreateSuperAdmin;
        private boolean canCreateAdmin;
        private boolean canCreateUserAccount;
        private boolean canManageAdminPermissions;
        private boolean canManageUserPermissions;
        private boolean canResetAdminPassword;
        private boolean canResetUserPassword;
        private boolean canPayUserOverdue;
        private boolean allowEditUserProfile;
        private boolean allowEditProfile;
        private boolean allowEditOtherAdminProfile;
        private boolean canManageTopics;
        private boolean canManageNews;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static AdminPermissionDTO fromEntity(AdminPermission e) {
            AdminPermissionDTO dto = new AdminPermissionDTO();
            dto.setId(e.getId());
            dto.setRoles(e.getRoles());
            dto.setCanLogin(e.isCanLogin());
            dto.setCanManageAnnouncement(e.isCanManageAnnouncement());
            dto.setCanManageHelpArticles(e.isCanManageHelpArticles());
            dto.setCanCreateSuperAdmin(e.isCanCreateSuperAdmin());
            dto.setCanCreateAdmin(e.isCanCreateAdmin());
            dto.setCanCreateUserAccount(e.isCanCreateUserAccount());
            dto.setCanManageAdminPermissions(e.isCanManageAdminPermissions());
            dto.setCanManageUserPermissions(e.isCanManageUserPermissions());
            dto.setCanResetAdminPassword(e.isCanResetAdminPassword());
            dto.setCanResetUserPassword(e.isCanResetUserPassword());
            dto.setCanPayUserOverdue(e.isCanPayUserOverdue());
            dto.setAllowEditUserProfile(e.isAllowEditUserProfile());
            dto.setAllowEditProfile(e.isAllowEditProfile());
            dto.setAllowEditOtherAdminProfile(e.isAllowEditOtherAdminProfile());
            dto.setCanManageTopics(e.isCanManageTopics());
            dto.setCanManageNews(e.isCanManageNews());
            dto.setCreatedAt(e.getCreatedAt());
            dto.setUpdatedAt(e.getUpdatedAt());
            return dto;
        }
    }

    /**
     * 创建管理员权限时使用的 DTO
     */
    @Data
    public static class CreateAdminPermissionDTO {
        private String roles;
        private boolean canLogin;
        private boolean canManageAnnouncement;
        private boolean canManageHelpArticles;
        private boolean canCreateSuperAdmin;
        private boolean canCreateAdmin;
        private boolean canCreateUserAccount;
        private boolean canManageAdminPermissions;
        private boolean canManageUserPermissions;
        private boolean canResetAdminPassword;
        private boolean canResetUserPassword;
        private boolean canPayUserOverdue;
        private boolean allowEditUserProfile;
        private boolean allowEditProfile;
        private boolean allowEditOtherAdminProfile;
        private boolean canManageTopics;
        private boolean canManageNews;

        public AdminPermission toEntity() {
            AdminPermission e = new AdminPermission();
            e.setRoles(this.getRoles());
            e.setCanLogin(this.isCanLogin());
            e.setCanManageAnnouncement(this.isCanManageAnnouncement());
            e.setCanManageHelpArticles(this.isCanManageHelpArticles());
            e.setCanCreateSuperAdmin(this.isCanCreateSuperAdmin());
            e.setCanCreateAdmin(this.isCanCreateAdmin());
            e.setCanCreateUserAccount(this.isCanCreateUserAccount());
            e.setCanManageAdminPermissions(this.isCanManageAdminPermissions());
            e.setCanManageUserPermissions(this.isCanManageUserPermissions());
            e.setCanResetAdminPassword(this.isCanResetAdminPassword());
            e.setCanResetUserPassword(this.isCanResetUserPassword());
            e.setCanPayUserOverdue(this.isCanPayUserOverdue());
            e.setAllowEditUserProfile(this.isAllowEditUserProfile());
            e.setAllowEditProfile(this.isAllowEditProfile());
            e.setAllowEditOtherAdminProfile(this.isAllowEditOtherAdminProfile());
            e.setCanManageTopics(this.isCanManageTopics());
            e.setCanManageNews(this.isCanManageNews());
            // 创建时自动设置时间
            e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());
            return e;
        }
    }

    /**
     * 更新管理员权限时使用的 DTO
     */
    @Data
    public static class UpdateAdminPermissionDTO {
        private Long id;
        private String roles;
        private boolean canLogin;
        private boolean canManageAnnouncement;
        private boolean canManageHelpArticles;
        private boolean canCreateSuperAdmin;
        private boolean canCreateAdmin;
        private boolean canCreateUserAccount;
        private boolean canManageAdminPermissions;
        private boolean canManageUserPermissions;
        private boolean canResetAdminPassword;
        private boolean canResetUserPassword;
        private boolean canPayUserOverdue;
        private boolean allowEditUserProfile;
        private boolean allowEditProfile;
        private boolean allowEditOtherAdminProfile;
        private boolean canManageTopics;
        private boolean canManageNews;

        public void applyToEntity(AdminPermission e) {
            e.setRoles(this.getRoles());
            e.setCanLogin(this.isCanLogin());
            e.setCanManageAnnouncement(this.isCanManageAnnouncement());
            e.setCanManageHelpArticles(this.isCanManageHelpArticles());
            e.setCanCreateSuperAdmin(this.isCanCreateSuperAdmin());
            e.setCanCreateAdmin(this.isCanCreateAdmin());
            e.setCanCreateUserAccount(this.isCanCreateUserAccount());
            e.setCanManageAdminPermissions(this.isCanManageAdminPermissions());
            e.setCanManageUserPermissions(this.isCanManageUserPermissions());
            e.setCanResetAdminPassword(this.isCanResetAdminPassword());
            e.setCanResetUserPassword(this.isCanResetUserPassword());
            e.setCanPayUserOverdue(this.isCanPayUserOverdue());
            e.setAllowEditUserProfile(this.isAllowEditUserProfile());
            e.setAllowEditProfile(this.isAllowEditProfile());
            e.setAllowEditOtherAdminProfile(this.isAllowEditOtherAdminProfile());
            e.setCanManageTopics(this.isCanManageTopics());
            e.setCanManageNews(this.isCanManageNews());
            // 更新时自动刷新修改时间
            e.setUpdatedAt(LocalDateTime.now());
        }
    }
}