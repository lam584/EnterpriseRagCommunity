package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.RiskLabelingEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RiskLabelingRepository extends JpaRepository<RiskLabelingEntity, Long>, JpaSpecificationExecutor<RiskLabelingEntity> {
    // 唯一查询：target_type + target_id + tag_id + source
    Optional<RiskLabelingEntity> findByTargetTypeAndTargetIdAndTagIdAndSource(ContentType targetType, Long targetId, Long tagId, com.example.EnterpriseRagCommunity.entity.moderation.enums.Source source);

    // 时间范围
    List<RiskLabelingEntity> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 组合：按目标/时间
    List<RiskLabelingEntity> findAllByTargetTypeAndTargetId(ContentType targetType, Long targetId);
    List<RiskLabelingEntity> findAllByTargetTypeAndTargetIdAndCreatedAtBetween(ContentType targetType, Long targetId, LocalDateTime start, LocalDateTime end);
}
