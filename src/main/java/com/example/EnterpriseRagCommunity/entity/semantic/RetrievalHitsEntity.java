package com.example.EnterpriseRagCommunity.entity.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "retrieval_hits")
public class RetrievalHitsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "`rank`", nullable = false)
    private Integer rank;

    @Enumerated(EnumType.STRING)
    @Column(name = "hit_type", nullable = false, length = 16)
    private RetrievalHitType hitType;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "chunk_id")
    private Long chunkId;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
