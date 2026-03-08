package com.example.EnterpriseRagCommunity.entity.content;

import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "post_attachments")
public class PostAttachmentsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "file_asset_id", nullable = false)
    private Long fileAssetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_asset_id", insertable = false, updatable = false)
    private FileAssetsEntity fileAsset;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
