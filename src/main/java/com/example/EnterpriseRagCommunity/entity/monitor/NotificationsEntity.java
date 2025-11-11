package com.example.EnterpriseRagCommunity.entity.monitor;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "notifications")
public class NotificationsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 外键统一使用 xxxId(Long)，对应 SQL: user_id BIGINT NOT NULL
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 对应 SQL: type VARCHAR(64) NOT NULL
    @Column(name = "type", length = 64, nullable = false)
    private String type;

    // 对应 SQL: title VARCHAR(191) NOT NULL
    @Column(name = "title", length = 191, nullable = false)
    private String title;

    // 对应 SQL: content TEXT NULL
    @Column(name = "content", columnDefinition = "text")
    private String content;

    // 对应 SQL: read_at DATETIME(3) NULL
    @Column(name = "read_at")
    private LocalDateTime readAt;

    // 对应 SQL: created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
