package com.example.EnterpriseRagCommunity.entity.access;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "user_roles")
public class UserRolesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "roles", nullable = false, length = 64)
    private String roles;

    @Column(name = "can_login", nullable = false)
    private Boolean canLogin;

    @Column(name = "can_view_announcement", nullable = false)
    private Boolean canViewAnnouncement;

    @Column(name = "can_view_help_articles", nullable = false)
    private Boolean canViewHelpArticles;

    @Column(name = "can_reset_own_password", nullable = false)
    private Boolean canResetOwnPassword;

    @Column(name = "can_comment", nullable = false)
    private Boolean canComment;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
