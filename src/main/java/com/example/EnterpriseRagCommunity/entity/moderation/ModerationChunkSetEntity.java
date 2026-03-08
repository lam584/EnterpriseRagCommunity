package com.example.EnterpriseRagCommunity.entity.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_chunk_sets")
public class ModerationChunkSetEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "queue_id", nullable = false)
    private Long queueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false, length = 16)
    private ModerationCaseType caseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 16)
    private ContentType contentType;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ChunkSetStatus status;

    @Column(name = "chunk_threshold_chars")
    private Integer chunkThresholdChars;

    @Column(name = "chunk_size_chars")
    private Integer chunkSizeChars;

    @Column(name = "overlap_chars")
    private Integer overlapChars;

    @Column(name = "total_chunks", nullable = false)
    private Integer totalChunks = 0;

    @Column(name = "completed_chunks", nullable = false)
    private Integer completedChunks = 0;

    @Column(name = "failed_chunks", nullable = false)
    private Integer failedChunks = 0;

    @Convert(converter = com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter.class)
    @Column(name = "memory_json", columnDefinition = "longtext")
    private Map<String, Object> memoryJson;

    @Convert(converter = com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter.class)
    @Column(name = "config_json", columnDefinition = "longtext")
    private Map<String, Object> configJson;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
