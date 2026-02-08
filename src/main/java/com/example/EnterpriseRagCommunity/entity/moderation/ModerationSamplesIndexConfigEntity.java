package com.example.EnterpriseRagCommunity.entity.moderation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_samples_index_config")
public class ModerationSamplesIndexConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "index_name", nullable = false, length = 128)
    private String indexName;

    @Column(name = "ik_enabled", nullable = false)
    private Boolean ikEnabled;

    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    @Column(name = "embedding_dims", nullable = false)
    private Integer embeddingDims;

    @Column(name = "default_top_k", nullable = false)
    private Integer defaultTopK;

    @Column(name = "default_threshold", nullable = false)
    private Double defaultThreshold;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}

