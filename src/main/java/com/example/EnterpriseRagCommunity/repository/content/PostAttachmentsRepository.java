package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostAttachmentsRepository extends JpaRepository<PostAttachmentsEntity, Long>, JpaSpecificationExecutor<PostAttachmentsEntity> {
    Page<PostAttachmentsEntity> findByPostId(Long postId, Pageable pageable);

    List<PostAttachmentsEntity> findByPostId(Long postId);

    void deleteByPostId(Long postId);

    List<PostAttachmentsEntity> findByFileAssetIdIn(List<Long> fileAssetIds);

    interface AdminPostFileRow {
        Long getAttachmentId();

        Long getPostId();

        Long getFileAssetId();

        String getAttachmentUrl();

        String getFileName();

        String getAttachmentMimeType();

        Long getAttachmentSizeBytes();

        Integer getWidth();

        Integer getHeight();

        LocalDateTime getAttachmentCreatedAt();

        String getAssetUrl();

        String getOriginalName();

        String getAssetMimeType();

        Long getAssetSizeBytes();

        String getExtractStatus();

        LocalDateTime getExtractionUpdatedAt();

        String getExtractedMetadataJson();

        String getExtractionErrorMessage();
    }

    interface AdminPostFileDetailRow extends AdminPostFileRow {
        String getExtractedText();
    }

    @Query(
            value = """
                    SELECT
                      pa.id AS attachmentId,
                      pa.post_id AS postId,
                      pa.file_asset_id AS fileAssetId,
                      fa.url AS attachmentUrl,
                      fa.original_name AS fileName,
                      fa.mime_type AS attachmentMimeType,
                      fa.size_bytes AS attachmentSizeBytes,
                      pa.width AS width,
                      pa.height AS height,
                      pa.created_at AS attachmentCreatedAt,
                      fa.url AS assetUrl,
                      fa.original_name AS originalName,
                      fa.mime_type AS assetMimeType,
                      fa.size_bytes AS assetSizeBytes,
                      fe.extract_status AS extractStatus,
                      fe.updated_at AS extractionUpdatedAt,
                      fe.extracted_metadata_json AS extractedMetadataJson,
                      fe.error_message AS extractionErrorMessage
                    FROM post_attachments pa
                      JOIN file_assets fa ON fa.id = pa.file_asset_id
                      LEFT JOIN file_asset_extractions fe ON fe.file_asset_id = pa.file_asset_id
                    WHERE (:postId IS NULL OR pa.post_id = :postId)
                      AND (:fileAssetId IS NULL OR pa.file_asset_id = :fileAssetId)
                      AND (
                        :keyword IS NULL OR :keyword = ''
                        OR fa.original_name LIKE CONCAT('%', :keyword, '%')
                      )
                      AND (
                        :extractStatus IS NULL OR :extractStatus = ''
                        OR (:extractStatus = 'NONE' AND fe.file_asset_id IS NULL)
                        OR (:extractStatus <> 'NONE' AND fe.extract_status = :extractStatus)
                      )
                    ORDER BY pa.created_at DESC, pa.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM post_attachments pa
                      JOIN file_assets fa ON fa.id = pa.file_asset_id
                      LEFT JOIN file_asset_extractions fe ON fe.file_asset_id = pa.file_asset_id
                    WHERE (:postId IS NULL OR pa.post_id = :postId)
                      AND (:fileAssetId IS NULL OR pa.file_asset_id = :fileAssetId)
                      AND (
                        :keyword IS NULL OR :keyword = ''
                        OR fa.original_name LIKE CONCAT('%', :keyword, '%')
                      )
                      AND (
                        :extractStatus IS NULL OR :extractStatus = ''
                        OR (:extractStatus = 'NONE' AND fe.file_asset_id IS NULL)
                        OR (:extractStatus <> 'NONE' AND fe.extract_status = :extractStatus)
                      )
                    """,
            nativeQuery = true
    )
    Page<AdminPostFileRow> adminListPostFiles(
            @Param("postId") Long postId,
            @Param("fileAssetId") Long fileAssetId,
            @Param("keyword") String keyword,
            @Param("extractStatus") String extractStatus,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT
                      pa.id AS attachmentId,
                      pa.post_id AS postId,
                      pa.file_asset_id AS fileAssetId,
                      fa.url AS attachmentUrl,
                      fa.original_name AS fileName,
                      fa.mime_type AS attachmentMimeType,
                      fa.size_bytes AS attachmentSizeBytes,
                      pa.width AS width,
                      pa.height AS height,
                      pa.created_at AS attachmentCreatedAt,
                      fa.url AS assetUrl,
                      fa.original_name AS originalName,
                      fa.mime_type AS assetMimeType,
                      fa.size_bytes AS assetSizeBytes,
                      fe.extract_status AS extractStatus,
                      fe.updated_at AS extractionUpdatedAt,
                      fe.extracted_metadata_json AS extractedMetadataJson,
                      fe.error_message AS extractionErrorMessage,
                      fe.extracted_text AS extractedText
                    FROM post_attachments pa
                      JOIN file_assets fa ON fa.id = pa.file_asset_id
                      LEFT JOIN file_asset_extractions fe ON fe.file_asset_id = pa.file_asset_id
                    WHERE pa.id = :attachmentId
                    LIMIT 1
                    """,
            nativeQuery = true
    )
    Optional<AdminPostFileDetailRow> adminGetPostFileDetail(@Param("attachmentId") Long attachmentId);
}
