package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ModerationPipelineStepRepository extends JpaRepository<ModerationPipelineStepEntity, Long> {
    List<ModerationPipelineStepEntity> findAllByRunIdOrderByStepOrderAsc(Long runId);
    Optional<ModerationPipelineStepEntity> findByRunIdAndStageAndStepOrder(Long runId, ModerationPipelineStepEntity.Stage stage, Integer stepOrder);
    List<ModerationPipelineStepEntity> findAllByRunIdAndStageOrderByStepOrderAsc(Long runId, ModerationPipelineStepEntity.Stage stage);

    List<ModerationPipelineStepEntity> findAllByRunIdIn(Collection<Long> runIds);

    long countByEndedAtAfterAndStageAndDecisionInAndScoreGreaterThanEqual(
            java.time.LocalDateTime since,
            ModerationPipelineStepEntity.Stage stage,
            Collection<String> decisions,
            java.math.BigDecimal minScore
    );
}
