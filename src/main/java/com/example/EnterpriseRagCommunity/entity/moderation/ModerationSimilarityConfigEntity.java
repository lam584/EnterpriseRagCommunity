package com.example.EnterpriseRagCommunity.entity.moderation;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_similarity_config")
public class ModerationSimilarityConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    @Column(name = "embedding_dims", nullable = false)
    private Integer embeddingDims;

    @Column(name = "max_input_chars", nullable = false)
    private Integer maxInputChars;

    @Column(name = "default_top_k", nullable = false)
    private Integer defaultTopK;

    @Column(name = "default_num_candidates", nullable = false)
    private Integer defaultNumCandidates;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
