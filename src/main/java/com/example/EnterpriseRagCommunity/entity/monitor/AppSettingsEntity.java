package com.example.EnterpriseRagCommunity.entity.monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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

    @Lob
    @Column(name = "v", nullable = false, columnDefinition = "LONGTEXT")
    private String v;
}
