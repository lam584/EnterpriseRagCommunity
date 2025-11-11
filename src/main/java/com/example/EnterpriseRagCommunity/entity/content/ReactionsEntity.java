package com.example.EnterpriseRagCommunity.entity.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "reactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_react", columnNames = {"user_id", "target_type", "target_id", "type"}),
        indexes = @Index(name = "idx_react_target", columnList = "target_type,target_id"))
public class ReactionsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 外键按照规范使用 Long 类型命名为 userId
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private ReactionTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ReactionType type;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime createdAt;
}
