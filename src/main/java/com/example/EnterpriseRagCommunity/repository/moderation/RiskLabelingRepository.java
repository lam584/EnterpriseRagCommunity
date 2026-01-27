package com.example.EnterpriseRagCommunity.repository.moderation;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.EnterpriseRagCommunity.entity.moderation.RiskLabelingEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;

@Repository
public interface RiskLabelingRepository extends JpaRepository<RiskLabelingEntity, Long>, JpaSpecificationExecutor<RiskLabelingEntity> {
    // 唯一查询：target_type + target_id + tag_id + source
    Optional<RiskLabelingEntity> findByTargetTypeAndTargetIdAndTagIdAndSource(ContentType targetType, Long targetId, Long tagId, com.example.EnterpriseRagCommunity.entity.moderation.enums.Source source);

    // 时间范围
    List<RiskLabelingEntity> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 组合：按目标/时间
    List<RiskLabelingEntity> findAllByTargetTypeAndTargetId(ContentType targetType, Long targetId);
    List<RiskLabelingEntity> findAllByTargetTypeAndTargetIdAndCreatedAtBetween(ContentType targetType, Long targetId, LocalDateTime start, LocalDateTime end);
    List<RiskLabelingEntity> findAllByTargetTypeAndTargetIdIn(ContentType targetType, Collection<Long> targetIds);

    void deleteAllByTargetTypeAndTargetId(ContentType targetType, Long targetId);
    void deleteAllByTargetTypeAndTargetIdAndSource(ContentType targetType, Long targetId, com.example.EnterpriseRagCommunity.entity.moderation.enums.Source source);

    boolean existsByTagId(Long tagId);

    interface TagUsageCount {
        Long getTagId();
        Long getUsageCount();
    }

    @Query("select rl.tagId as tagId, count(rl) as usageCount from RiskLabelingEntity rl where rl.tagId in :tagIds group by rl.tagId")
    List<TagUsageCount> countUsageByTagIds(@Param("tagIds") Collection<Long> tagIds);
}
