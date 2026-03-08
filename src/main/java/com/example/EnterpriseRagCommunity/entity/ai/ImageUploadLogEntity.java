package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "image_upload_logs")
public class ImageUploadLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "local_path", length = 500, nullable = false)
    private String localPath;

    @Column(name = "remote_url", length = 2000, nullable = false)
    private String remoteUrl;

    @Column(name = "storage_mode", length = 30, nullable = false)
    private String storageMode;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "upload_duration_ms")
    private Integer uploadDurationMs;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "status", length = 20, nullable = false)
    private String status;
}
