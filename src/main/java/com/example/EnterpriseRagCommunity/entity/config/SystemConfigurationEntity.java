package com.example.EnterpriseRagCommunity.entity.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "system_configurations")
public class SystemConfigurationEntity {
    @Id
    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value", length = 2048)
    private String configValue;

    @Column(name = "is_encrypted")
    private boolean isEncrypted;

    @Column(name = "description")
    private String description;
}
