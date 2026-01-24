package com.example.EnterpriseRagCommunity.entity.monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "app_settings")
public class AppSettingsEntity {
    @Id
    @Column(name = "k", length = 64, nullable = false)
    private String k;

    @Column(name = "v", length = 255, nullable = false)
    private String v;
}

