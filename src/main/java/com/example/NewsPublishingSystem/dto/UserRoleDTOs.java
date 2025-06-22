// 文件：java/com/example/NewsPublishingSystem/dto/UserRoleDTOs.java
package com.example.NewsPublishingSystem.dto;

import com.example.NewsPublishingSystem.entity.UserRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UserRole 相关 DTO 定义
 */
public class UserRoleDTOs {

    /**
     * 用于前端展示／查询的 DTO
     */
    @Data
    public static class UserRoleDTO {
        private Long id;
        private String roles;
        private boolean canLogin;
        private boolean canViewAnnouncement;
        private boolean canViewHelpArticles;
        private boolean canResetOwnPassword;
        private boolean canComment;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        /**
         * 从 Entity 转为 DTO
         */
        public static UserRoleDTO fromEntity(UserRole e) {
            UserRoleDTO dto = new UserRoleDTO();
            dto.setId(e.getId());
            dto.setRoles(e.getRoles());
            dto.setCanLogin(e.isCanLogin());
            dto.setCanViewAnnouncement(e.isCanViewAnnouncement());
            dto.setCanViewHelpArticles(e.isCanViewHelpArticles());
            dto.setCanResetOwnPassword(e.isCanResetOwnPassword());
            dto.setCanComment(e.isCanComment());
            dto.setNotes(e.getNotes());
            dto.setCreatedAt(e.getCreatedAt());
            dto.setUpdatedAt(e.getUpdatedAt());
            return dto;
        }
    }

    /**
     * 创建 UserRole 时使用的 DTO
     */
    @Data
    public static class CreateUserRoleDTO {
        private String roles;
        private boolean canLogin;
        private boolean canViewAnnouncement;
        private boolean canViewHelpArticles;
        private boolean canResetOwnPassword;
        private boolean canComment;
        private String notes;

        /**
         * 转成 Entity
         */
        public UserRole toEntity() {
            UserRole e = new UserRole();
            e.setRoles(this.getRoles());
            e.setCanLogin(this.isCanLogin());
            e.setCanViewAnnouncement(this.isCanViewAnnouncement());
            e.setCanViewHelpArticles(this.isCanViewHelpArticles());
            e.setCanResetOwnPassword(this.isCanResetOwnPassword());
            e.setCanComment(this.isCanComment());
            e.setNotes(this.getNotes());
            // 设置时间戳
            LocalDateTime now = LocalDateTime.now();
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            return e;
        }
    }

    /**
     * 更新 UserRole 时使用的 DTO
     */
    @Data
    public static class UpdateUserRoleDTO {
        private Long id;
        private String roles;
        private boolean canLogin;
        private boolean canViewAnnouncement;
        private boolean canViewHelpArticles;
        private boolean canResetOwnPassword;
        private boolean canComment;
        private String notes;

        /**
         * 把 DTO 的值写入已有的 Entity，并更新更新时间
         */
        public void applyToEntity(UserRole e) {
            // id 通常不用更新
            e.setRoles(this.getRoles());
            e.setCanLogin(this.isCanLogin());
            e.setCanViewAnnouncement(this.isCanViewAnnouncement());
            e.setCanViewHelpArticles(this.isCanViewHelpArticles());
            e.setCanResetOwnPassword(this.isCanResetOwnPassword());
            e.setCanComment(this.isCanComment());
            e.setNotes(this.getNotes());
            e.setUpdatedAt(LocalDateTime.now());
        }
    }
}