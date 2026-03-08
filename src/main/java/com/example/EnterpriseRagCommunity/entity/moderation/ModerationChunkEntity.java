package com.example.EnterpriseRagCommunity.entity.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_chunks")
public class ModerationChunkEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "chunk_set_id", nullable = false)
    private Long chunkSetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private ChunkSourceType sourceType;

    @Column(name = "source_key", nullable = false, length = 64)
    private String sourceKey;

    @Column(name = "file_asset_id")
    private Long fileAssetId;

    @Column(name = "file_name", length = 191)
    private String fileName;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "start_offset", nullable = false)
    private Integer startOffset;

    @Column(name = "end_offset", nullable = false)
    private Integer endOffset;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ChunkStatus status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "model", length = 64)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", length = 16)
    private Verdict verdict;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Convert(converter = com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter.class)
    @Column(name = "labels", columnDefinition = "longtext")
    private Map<String, Object> labels;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
