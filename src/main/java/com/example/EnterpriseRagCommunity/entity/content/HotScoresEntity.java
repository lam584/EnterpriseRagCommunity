package com.example.EnterpriseRagCommunity.entity.content;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "hot_scores")
public class HotScoresEntity {
    // 主键=外键: 对 posts.id 的一对一缓存。规范要求显式映射列并 nullable=false
    @Id
    @Column(name = "post_id", nullable = false)
    private Long postId;

    // 根据规范不再保留对象引用（禁止使用对象实体引用，统一使用 xxxId），如需访问帖子请在 Service 层通过 postId 查询
    // ...existing code...

    @Column(name = "score_24h", nullable = false)
    private Double score24h;

    @Column(name = "score_7d", nullable = false)
    private Double score7d;

    @Column(name = "score_all", nullable = false)
    private Double scoreAll;

    @Column(name = "decay_base", nullable = false)
    private Double decayBase;

    @Column(name = "last_recalculated_at", nullable = false)
    private LocalDateTime lastRecalculatedAt;
}
