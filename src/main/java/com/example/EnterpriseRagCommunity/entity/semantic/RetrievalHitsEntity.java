package com.example.EnterpriseRagCommunity.entity.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "chunk_id")
    private Long chunkId;

    @Column(name = "score", nullable = false)
    private Double score;
}
