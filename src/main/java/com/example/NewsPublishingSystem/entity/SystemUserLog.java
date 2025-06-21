package com.example.NewsPublishingSystem.entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// ======================================================
// 9. SystemUserLog
// ======================================================
@Entity
@Table(name = "system_user_logs",
        indexes = {@Index(name = "idx_user_logs_level_time", columnList = "level,created_at")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemUserLog {

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

    @Column(name = "user_agent", nullable = false)
    private String userAgent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}