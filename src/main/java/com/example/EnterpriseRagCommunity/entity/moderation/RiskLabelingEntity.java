package com.example.EnterpriseRagCommunity.entity.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Source;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "risk_labeling",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_rl", columnNames = {"target_type", "target_id", "tag_id", "source"})
        }
)
public class RiskLabelingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private ContentType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    // 外键使用 Long xxxId，显式映射列名
    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private Source source;

    @Column(name = "confidence", precision = 5, scale = 4)
    private java.math.BigDecimal confidence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
