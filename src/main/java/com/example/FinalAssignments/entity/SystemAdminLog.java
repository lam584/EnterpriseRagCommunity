package com.example.FinalAssignments.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "system_Admin_logs",
        indexes = @Index(name = "idx_admin_logs_level_time", columnList = "level, created_at")
)
public class SystemAdminLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String level;

    @Column(nullable = false, length = 255)
    private String message;

    @Lob
    @Column(nullable = false)
    private String context;

    @Column(nullable = false, length = 45)
    private String ip;

    /** 注意列名是 Admin_user_agent */
    @Column(name = "Admin_user_agent", nullable = false, length = 255)
    private String adminUserAgent;

    /** 管理员 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Admin_id", nullable = false)
    private Administrator administrator;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}