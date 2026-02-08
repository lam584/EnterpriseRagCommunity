package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "llm_provider_settings")
public class LlmProviderSettingsEntity {

    @Id
    @Column(name = "env", length = 32, nullable = false)
    private String env;

    @Column(name = "active_provider_id", length = 64)
    private String activeProviderId;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
