package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ModerationChunkRepository extends JpaRepository<ModerationChunkEntity, Long> {
    List<ModerationChunkEntity> findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(Long chunkSetId);

    long countByChunkSetIdAndStatusIn(Long chunkSetId, Collection<ChunkStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ModerationChunkEntity c where c.id = :id")
    List<ModerationChunkEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(c) from ModerationChunkEntity c
            where c.chunkSetId = :chunkSetId
              and (
                   c.status = :pendingStatus
                or (c.status = :failedStatus and coalesce(c.attempts, 0) < :maxAttempts)
              )
            """)
    long countRetriableByChunkSetId(@Param("chunkSetId") Long chunkSetId,
                                   @Param("pendingStatus") ChunkStatus pendingStatus,
                                   @Param("failedStatus") ChunkStatus failedStatus,
                                   @Param("maxAttempts") int maxAttempts);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ModerationChunkEntity c where c.chunkSetId = :chunkSetId and c.status in :statuses order by c.chunkIndex asc")
    List<ModerationChunkEntity> findNextForUpdate(@Param("chunkSetId") Long chunkSetId,
                                                  @Param("statuses") Collection<ChunkStatus> statuses,
                                                  Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from ModerationChunkEntity c
            where c.chunkSetId = :chunkSetId
              and (
                   c.status = :pendingStatus
                or (c.status = :failedStatus and coalesce(c.attempts, 0) < :maxAttempts)
              )
            order by c.chunkIndex asc, c.id asc
            """)
    List<ModerationChunkEntity> findNextEligibleForUpdate(@Param("chunkSetId") Long chunkSetId,
                                                          @Param("pendingStatus") ChunkStatus pendingStatus,
                                                          @Param("failedStatus") ChunkStatus failedStatus,
                                                          @Param("maxAttempts") int maxAttempts,
                                                          Pageable pageable);

    @Query("""
            select c from ModerationChunkEntity c, ModerationChunkSetEntity s
            where s.id = c.chunkSetId
              and (:queueId is null or s.queueId = :queueId)
              and (:status is null or c.status = :status)
              and (:verdict is null or c.verdict = :verdict)
              and (:sourceType is null or c.sourceType = :sourceType)
              and (:fileAssetId is null or c.fileAssetId = :fileAssetId)
              and (:keyword is null or (
                     lower(coalesce(c.fileName, '')) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(c.model, '')) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(c.lastError, '')) like lower(concat('%', :keyword, '%'))
                  or lower(coalesce(c.sourceKey, '')) like lower(concat('%', :keyword, '%'))
              ))
            order by c.updatedAt desc
            """)
    List<ModerationChunkEntity> findRecentForAdmin(@Param("queueId") Long queueId,
                                                   @Param("status") ChunkStatus status,
                                                   @Param("verdict") Verdict verdict,
                                                   @Param("sourceType") ChunkSourceType sourceType,
                                                   @Param("fileAssetId") Long fileAssetId,
                                                   @Param("keyword") String keyword,
                                                   Pageable pageable);
}
