package com.example.NewsHubCommunity.entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
// ======================================================
// 3. UserRole
// ======================================================
@Entity
@Table(name = "user_roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "roles", nullable = false, unique = true)
    private String roles;

    @Column(name = "can_login", nullable = false)
    private boolean canLogin;

    @Column(name = "can_view_announcement", nullable = false)
    private boolean canViewAnnouncement;

    @Column(name = "can_view_help_articles", nullable = false)
    private boolean canViewHelpArticles;

    @Column(name = "can_reset_own_password", nullable = false)
    private boolean canResetOwnPassword;

    @Column(name = "can_comment", nullable = false)
    private boolean canComment;

    @Column(name = "notes", nullable = false, unique = true)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
