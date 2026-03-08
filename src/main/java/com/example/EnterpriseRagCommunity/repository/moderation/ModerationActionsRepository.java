package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationActionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ModerationActionsRepository extends JpaRepository<ModerationActionsEntity, Long>, JpaSpecificationExecutor<ModerationActionsEntity> {
    List<ModerationActionsEntity> findAllByQueueId(Long queueId);
    List<ModerationActionsEntity> findAllByAction(ActionType action);
    List<ModerationActionsEntity> findAllByActorUserId(Long actorUserId);

    // 时间范围
    List<ModerationActionsEntity> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByQueueIdAndReasonAndCreatedAtAfter(Long queueId, String reason, LocalDateTime createdAt);

    long countByQueueIdAndReasonAndCreatedAtBetween(Long queueId, String reason, LocalDateTime start, LocalDateTime end);
}
