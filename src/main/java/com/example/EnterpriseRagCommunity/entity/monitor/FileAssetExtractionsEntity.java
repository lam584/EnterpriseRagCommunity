package com.example.EnterpriseRagCommunity.entity.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "file_asset_extractions")
public class FileAssetExtractionsEntity {
    @Id
    @Column(name = "file_asset_id", nullable = false)
    private Long fileAssetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "extract_status", nullable = false, length = 16)
    private FileAssetExtractionStatus extractStatus = FileAssetExtractionStatus.PENDING;

    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    private String extractedText;

    @Column(name = "extracted_metadata_json", columnDefinition = "LONGTEXT")
    private String extractedMetadataJson;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (extractStatus == null) extractStatus = FileAssetExtractionStatus.PENDING;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (extractStatus == null) extractStatus = FileAssetExtractionStatus.PENDING;
    }
}
