package com.example.EnterpriseRagCommunity.entity.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ActionType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_actions")
public class ModerationActionsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "queue_id", nullable = false)
    private Long queueId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private ActionType action;

    @Column(name = "reason", length = 255)
    private String reason;

    @Convert(converter = com.example.EnterpriseRagCommunity.entity.converter.JsonConverter.class)
    @Column(name = "snapshot", columnDefinition = "json")
    private Map<String, Object> snapshot;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
