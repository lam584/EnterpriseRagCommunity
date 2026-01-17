package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModerationPipelineStepRepository extends JpaRepository<ModerationPipelineStepEntity, Long> {
    List<ModerationPipelineStepEntity> findAllByRunIdOrderByStepOrderAsc(Long runId);
    Optional<ModerationPipelineStepEntity> findByRunIdAndStage(Long runId, ModerationPipelineStepEntity.Stage stage);
}
