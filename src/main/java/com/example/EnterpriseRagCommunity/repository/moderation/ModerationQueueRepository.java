package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ModerationQueueRepository extends JpaRepository<ModerationQueueEntity, Long>, JpaSpecificationExecutor<ModerationQueueEntity> {
    // 唯一查询：content_type + content_id，防止重复入队
    Optional<ModerationQueueEntity> findByContentTypeAndContentId(ContentType contentType, Long contentId);

    // 状态/阶段/指派/时间范围
    List<ModerationQueueEntity> findAllByStatus(QueueStatus status);
    List<ModerationQueueEntity> findAllByCurrentStage(QueueStage currentStage);
    List<ModerationQueueEntity> findAllByAssignedToId(Long assignedToId);
    List<ModerationQueueEntity> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 组合条件
    List<ModerationQueueEntity> findAllByStatusAndAssignedToId(QueueStatus status, Long assignedToId);
    List<ModerationQueueEntity> findAllByStatusAndCreatedAtBetween(QueueStatus status, LocalDateTime start, LocalDateTime end);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ModerationQueueEntity q set q.status = :newStatus, q.lockedBy = :lockedBy, q.lockedAt = :lockedAt, q.updatedAt = :lockedAt " +
            "where q.id = :id and q.status in (:allowedStatuses) and q.currentStage = :stage and (q.lockedAt is null or q.lockedAt < :lockExpiredBefore)")
    int tryLockForAutoRun(@org.springframework.data.repository.query.Param("id") Long id,
                          @org.springframework.data.repository.query.Param("stage") QueueStage stage,
                          @org.springframework.data.repository.query.Param("allowedStatuses") java.util.Collection<QueueStatus> allowedStatuses,
                          @org.springframework.data.repository.query.Param("newStatus") QueueStatus newStatus,
                          @org.springframework.data.repository.query.Param("lockedBy") String lockedBy,
                          @org.springframework.data.repository.query.Param("lockedAt") LocalDateTime lockedAt,
                          @org.springframework.data.repository.query.Param("lockExpiredBefore") LocalDateTime lockExpiredBefore);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ModerationQueueEntity q set q.assignedToId = :userId, q.updatedAt = :now " +
            "where q.id = :id and q.status = com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.HUMAN and q.assignedToId is null")
    int claimHuman(@org.springframework.data.repository.query.Param("id") Long id,
                   @org.springframework.data.repository.query.Param("userId") Long userId,
                   @org.springframework.data.repository.query.Param("now") LocalDateTime now);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ModerationQueueEntity q set q.assignedToId = null, q.updatedAt = :now where q.id = :id and q.assignedToId = :userId")
    int releaseHuman(@org.springframework.data.repository.query.Param("id") Long id,
                     @org.springframework.data.repository.query.Param("userId") Long userId,
                     @org.springframework.data.repository.query.Param("now") LocalDateTime now);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ModerationQueueEntity q set q.status = :status, q.currentStage = :stage, q.assignedToId = null, q.lockedBy = null, q.lockedAt = null, q.finishedAt = null, q.updatedAt = :now " +
            "where q.id = :id and q.status in (" +
            "com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.HUMAN, " +
            "com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.REVIEWING, " +
            "com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.PENDING, " +
            "com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.APPROVED, " +
            "com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.REJECTED)")
    int requeueToAuto(@org.springframework.data.repository.query.Param("id") Long id,
                      @org.springframework.data.repository.query.Param("status") QueueStatus status,
                      @org.springframework.data.repository.query.Param("stage") QueueStage stage,
                      @org.springframework.data.repository.query.Param("now") LocalDateTime now);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ModerationQueueEntity q set q.status = :status, q.currentStage = :stage, q.assignedToId = null, q.lockedBy = null, q.lockedAt = null, q.finishedAt = null, q.updatedAt = :now " +
            "where q.id = :id and q.status in (" +
            "com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.APPROVED, " +
            "com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.REJECTED)")
    int toHuman(@org.springframework.data.repository.query.Param("id") Long id,
                @org.springframework.data.repository.query.Param("status") QueueStatus status,
                @org.springframework.data.repository.query.Param("stage") QueueStage stage,
                @org.springframework.data.repository.query.Param("now") LocalDateTime now);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ModerationQueueEntity q set q.lockedBy = null, q.lockedAt = null, q.updatedAt = :now where q.id = :id and q.lockedBy = :lockedBy")
    int unlockAutoRun(@org.springframework.data.repository.query.Param("id") Long id,
                      @org.springframework.data.repository.query.Param("lockedBy") String lockedBy,
                      @org.springframework.data.repository.query.Param("now") LocalDateTime now);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ModerationQueueEntity q set q.currentStage = :stage, q.updatedAt = :now " +
            "where q.id = :id and q.status = com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.REVIEWING and q.lockedBy = :lockedBy")
    int updateStageIfLockedBy(@org.springframework.data.repository.query.Param("id") Long id,
                              @org.springframework.data.repository.query.Param("stage") QueueStage stage,
                              @org.springframework.data.repository.query.Param("lockedBy") String lockedBy,
                              @org.springframework.data.repository.query.Param("now") LocalDateTime now);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ModerationQueueEntity q set q.currentStage = :stage, q.updatedAt = :now " +
            "where q.id = :id and q.status in (" +
            "com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.PENDING, " +
            "com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.REVIEWING)")
    int updateStageIfPendingOrReviewing(@org.springframework.data.repository.query.Param("id") Long id,
                                        @org.springframework.data.repository.query.Param("stage") QueueStage stage,
                                        @org.springframework.data.repository.query.Param("now") LocalDateTime now);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update ModerationQueueEntity q set q.currentStage = :stage, q.status = :status, q.updatedAt = :now " +
            "where q.id = :id and q.status in (" +
            "com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.PENDING, " +
            "com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus.REVIEWING)")
    int updateStageAndStatusIfPendingOrReviewing(@org.springframework.data.repository.query.Param("id") Long id,
                                                 @org.springframework.data.repository.query.Param("stage") QueueStage stage,
                                                 @org.springframework.data.repository.query.Param("status") QueueStatus status,
                                                 @org.springframework.data.repository.query.Param("now") LocalDateTime now);
}
