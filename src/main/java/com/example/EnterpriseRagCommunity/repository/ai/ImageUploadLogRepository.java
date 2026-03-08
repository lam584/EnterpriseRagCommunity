package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.ImageUploadLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ImageUploadLogRepository extends JpaRepository<ImageUploadLogEntity, Long> {

    /** Cache lookup: find active upload for a given local path + model, not yet expired. */
    Optional<ImageUploadLogEntity> findFirstByLocalPathAndModelNameAndStatusAndExpiresAtAfterOrderByUploadedAtDesc(
            String localPath, String modelName, String status, LocalDateTime now);

    /** Cache lookup for OSS (no model binding, no expiry). */
    Optional<ImageUploadLogEntity> findFirstByLocalPathAndStorageModeAndStatusOrderByUploadedAtDesc(
            String localPath, String storageMode, String status);

    Page<ImageUploadLogEntity> findAllByOrderByUploadedAtDesc(Pageable pageable);

    @Modifying
    @Query("DELETE FROM ImageUploadLogEntity e WHERE e.status = 'EXPIRED' OR (e.expiresAt IS NOT NULL AND e.expiresAt < :now)")
    int deleteExpired(LocalDateTime now);
}
