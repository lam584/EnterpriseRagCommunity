package com.example.EnterpriseRagCommunity.entity.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotTargetType;
import com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "post_compose_ai_snapshots")
public class PostComposeAiSnapshotsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private PostComposeAiSnapshotTargetType targetType;

    @Column(name = "draft_id")
    private Long draftId;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "before_title", nullable = false, length = 191)
    private String beforeTitle;

    @Lob
    @Column(name = "before_content", nullable = false)
    private String beforeContent;

    @Column(name = "before_board_id", nullable = false)
    private Long beforeBoardId;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "before_metadata", columnDefinition = "json")
    private Map<String, Object> beforeMetadata;

    @Lob
    @Column(name = "after_content")
    private String afterContent;

    @Lob
    @Column(name = "instruction")
    private String instruction;

    @Column(name = "provider_id", length = 128)
    private String providerId;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "top_p")
    private Double topP;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PostComposeAiSnapshotStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}

