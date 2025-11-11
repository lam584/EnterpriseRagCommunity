package com.example.EnterpriseRagCommunity.entity.rag;

import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "qa_sessions")
public class QaSessionsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 外键字段采用 Long userId 映射，禁止对象引用，根据规范使用列名一致映射
    @Column(name = "user_id")
    private Long userId; // NULL 允许

    @Column(name = "title", length = 191)
    private String title; // NULL 允许

    @Enumerated(EnumType.STRING)
    @Column(name = "context_strategy", nullable = false, length = 16)
    private ContextStrategy contextStrategy; // NOT NULL

    @Column(name = "is_active", nullable = false)
    private Boolean isActive; // NOT NULL

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // NOT NULL 审计字段
}
