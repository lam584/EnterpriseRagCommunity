package com.example.EnterpriseRagCommunity.entity.semantic;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "retrieval_events")
public class RetrievalEventsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Lob
    @Column(name = "query_text", nullable = false)
    private String queryText;

    @Column(name = "bm25_k")
    private Integer bm25K;

    @Column(name = "vec_k")
    private Integer vecK;

    @Column(name = "hybrid_k")
    private Integer hybridK;

    @Column(name = "rerank_model", length = 64)
    private String rerankModel;

    @Column(name = "rerank_k")
    private Integer rerankK;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
