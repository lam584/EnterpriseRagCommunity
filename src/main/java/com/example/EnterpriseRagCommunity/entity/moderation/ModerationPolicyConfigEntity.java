package com.example.EnterpriseRagCommunity.entity.moderation;

import com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "moderation_policy_config",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_moderation_policy_config_content_type", columnNames = {"content_type"})
        }
)
public class ModerationPolicyConfigEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 16)
    private ContentType contentType;

    @Column(name = "policy_version", nullable = false, length = 64)
    private String policyVersion;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "config_json", nullable = false, columnDefinition = "json")
    private Map<String, Object> config;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}

