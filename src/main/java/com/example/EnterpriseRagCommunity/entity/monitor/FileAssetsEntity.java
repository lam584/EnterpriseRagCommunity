package com.example.EnterpriseRagCommunity.entity.monitor;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "file_assets")
public class FileAssetsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // owner_user_id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private UsersEntity owner;

    @Column(name = "path", length = 512, nullable = false)
    private String path;

    @Column(name = "url", length = 512, nullable = false)
    private String url;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "mime_type", length = 64, nullable = false)
    private String mimeType;

    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private FileAssetStatus status = FileAssetStatus.READY;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = FileAssetStatus.READY;
        }
    }
}
