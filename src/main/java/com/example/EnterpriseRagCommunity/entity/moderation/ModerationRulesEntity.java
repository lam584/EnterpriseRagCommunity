package com.example.EnterpriseRagCommunity.entity.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.RuleType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "moderation_rules")
public class ModerationRulesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 96)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private RuleType type;

    @Lob
    @Column(name = "pattern", nullable = false)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private Severity severity;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Convert(converter = com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter.class)
    @Column(name = "metadata", columnDefinition = "json")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
