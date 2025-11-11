package com.example.EnterpriseRagCommunity.entity.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "audit_logs")
public class AuditLogsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private AuditResult result;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "details", columnDefinition = "json")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
