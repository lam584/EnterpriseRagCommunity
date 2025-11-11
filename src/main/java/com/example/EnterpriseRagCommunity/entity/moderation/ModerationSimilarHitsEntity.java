package com.example.EnterpriseRagCommunity.entity.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_similar_hits")
public class ModerationSimilarHitsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 16)
    private ContentType contentType;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "candidate_id")
    private Long candidateId;

    @Column(name = "distance", nullable = false)
    private Double distance;

    @Column(name = "threshold", nullable = false)
    private Double threshold;

    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt;
}

