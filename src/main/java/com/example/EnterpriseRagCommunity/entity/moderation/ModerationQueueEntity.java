package com.example.EnterpriseRagCommunity.entity.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_queue")
public class ModerationQueueEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 16)
    private ContentType contentType;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private QueueStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false, length = 16)
    private QueueStage currentStage;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "assigned_to")
    private Long assignedToId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
