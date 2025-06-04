package com.example.FinalAssignments.dto;

import com.example.FinalAssignments.entity.AdminPermission;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员权限数据传输对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPermissionDTO {
    private Long id;
    private String roles;
    private Boolean canLogin;
    private Boolean canManageAnnouncement;
    private Boolean canManageHelpArticles;
    private Boolean canCreateSuperAdmin;
    private Boolean canCreateAdmin;
    private Boolean canCreateUserAccount;
    private Boolean canManageAdminPermissions;
    private Boolean canManageUserPermissions;
    private Boolean canResetAdminPassword;
    private Boolean canResetUserPassword;
    private Boolean canPayUserOverdue;
    private Boolean canLendBooksToUser;
    private Boolean canReturnBooksForUser;
    private Boolean allowEditReadersProfile;
    private Boolean allowEditProfile;
    private Boolean allowEditOtherAdminProfile;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 管理员权限DTO转换器
     */
    public static class Converter {
        /**
         * 实体转DTO
         */
        public static AdminPermissionDTO fromEntity(AdminPermission entity) {
            if (entity == null) {
                return null;
            }

            AdminPermissionDTO dto = new AdminPermissionDTO();
            BeanUtils.copyProperties(entity, dto);

            return dto;
        }

        /**
         * DTO转实体
         */
        public static AdminPermission toEntity(AdminPermissionDTO dto) {
            if (dto == null) {
                return null;
            }

            AdminPermission entity = new AdminPermission();
            BeanUtils.copyProperties(dto, entity);

            // 确保创建和更新时间
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(LocalDateTime.now());
            }
            if (entity.getUpdatedAt() == null) {
                entity.setUpdatedAt(LocalDateTime.now());
            }

            return entity;
        }

        /**
         * 更新实体
         */
        public static void updateEntity(AdminPermissionDTO dto, AdminPermission entity) {
            if (dto == null || entity == null) {
                return;
            }

            // 只更新非空字段
            if (dto.getRoles() != null) entity.setRoles(dto.getRoles());
            if (dto.getCanLogin() != null) entity.setCanLogin(dto.getCanLogin());
            if (dto.getCanManageAnnouncement() != null) entity.setCanManageAnnouncement(dto.getCanManageAnnouncement());
            if (dto.getCanManageHelpArticles() != null) entity.setCanManageHelpArticles(dto.getCanManageHelpArticles());
            if (dto.getCanCreateSuperAdmin() != null) entity.setCanCreateSuperAdmin(dto.getCanCreateSuperAdmin());
            if (dto.getCanCreateAdmin() != null) entity.setCanCreateAdmin(dto.getCanCreateAdmin());
            if (dto.getCanCreateUserAccount() != null) entity.setCanCreateUserAccount(dto.getCanCreateUserAccount());
            if (dto.getCanManageAdminPermissions() != null) entity.setCanManageAdminPermissions(dto.getCanManageAdminPermissions());
            if (dto.getCanManageUserPermissions() != null) entity.setCanManageUserPermissions(dto.getCanManageUserPermissions());
            if (dto.getCanResetAdminPassword() != null) entity.setCanResetAdminPassword(dto.getCanResetAdminPassword());
            if (dto.getCanResetUserPassword() != null) entity.setCanResetUserPassword(dto.getCanResetUserPassword());
            if (dto.getCanPayUserOverdue() != null) entity.setCanPayUserOverdue(dto.getCanPayUserOverdue());
            if (dto.getCanLendBooksToUser() != null) entity.setCanLendBooksToUser(dto.getCanLendBooksToUser());
            if (dto.getCanReturnBooksForUser() != null) entity.setCanReturnBooksForUser(dto.getCanReturnBooksForUser());
            if (dto.getAllowEditReadersProfile() != null) entity.setAllowEditReadersProfile(dto.getAllowEditReadersProfile());
            if (dto.getAllowEditProfile() != null) entity.setAllowEditProfile(dto.getAllowEditProfile());
            if (dto.getAllowEditOtherAdminProfile() != null) entity.setAllowEditOtherAdminProfile(dto.getAllowEditOtherAdminProfile());

            // 总是更新更新时间
            entity.setUpdatedAt(LocalDateTime.now());
        }

        /**
         * 实体列表转DTO列表
         */
        public static List<AdminPermissionDTO> fromEntityList(List<AdminPermission> entityList) {
            if (entityList == null) {
                return null;
            }
            return entityList.stream()
                    .map(Converter::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
