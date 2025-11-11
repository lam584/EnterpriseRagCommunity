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
}
