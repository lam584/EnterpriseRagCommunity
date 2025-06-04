package com.example.FinalAssignments.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "admin_permissions")
public class AdminPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 角色 */
    @Column(name = "roles", nullable = false, unique = true, length = 100)
    private String roles;

    @Column(name = "can_login", nullable = false)
    private Boolean canLogin;

    @Column(name = "can_manage_announcement", nullable = false)
    private Boolean canManageAnnouncement;

    @Column(name = "can_manage_help_articles", nullable = false)
    private Boolean canManageHelpArticles;

    @Column(name = "can_create_super_admin", nullable = false)
    private Boolean canCreateSuperAdmin;

    @Column(name = "can_create_admin", nullable = false)
    private Boolean canCreateAdmin;

    @Column(name = "can_create_user_account", nullable = false)
    private Boolean canCreateUserAccount;

    @Column(name = "can_manage_admin_permissions", nullable = false)
    private Boolean canManageAdminPermissions;

    @Column(name = "can_manage_user_permissions", nullable = false)
    private Boolean canManageUserPermissions;

    @Column(name = "can_reset_admin_password", nullable = false)
    private Boolean canResetAdminPassword;

    @Column(name = "can_reset_user_password", nullable = false)
    private Boolean canResetUserPassword;

    @Column(name = "can_pay_user_overdue", nullable = false)
    private Boolean canPayUserOverdue;

    @Column(name = "can_lend_books_to_user", nullable = false)
    private Boolean canLendBooksToUser;

    @Column(name = "can_return_books_for_user", nullable = false)
    private Boolean canReturnBooksForUser;

    @Column(name = "allow_edit_readers_profile", nullable = false)
    private Boolean allowEditReadersProfile;

    @Column(name = "allow_edit_profile", nullable = false)
    private Boolean allowEditProfile;

    @Column(name = "allow_edit_other_admin_profile", nullable = false)
    private Boolean allowEditOtherAdminProfile;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}