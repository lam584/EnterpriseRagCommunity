package com.example.NewsPublishingSystem.entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// ======================================================
// 8. SystemAdminLog
// ======================================================
@Entity
@Table(name = "system_admin_logs",
        indexes = {@Index(name = "idx_admin_logs_level_time", columnList = "level,created_at")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemAdminLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String level;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String context;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(name = "admin_user_agent", nullable = false)
    private String adminUserAgent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Administrator admin;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}