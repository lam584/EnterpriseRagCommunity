package com.example.EnterpriseRagCommunity.repository.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.GenerationJobsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationJobType;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.GenerationTargetType; // added
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GenerationJobsRepository extends JpaRepository<GenerationJobsEntity, Long>, JpaSpecificationExecutor<GenerationJobsEntity> {
    // by type/status
    List<GenerationJobsEntity> findByJobType(GenerationJobType jobType);
    List<GenerationJobsEntity> findByStatus(GenerationJobStatus status);

    // by prompt FK
    List<GenerationJobsEntity> findByPromptId(Long promptId);

    // by time
    List<GenerationJobsEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // composite: target_type + target_id + status
    List<GenerationJobsEntity> findByTargetTypeAndTargetIdAndStatus(GenerationTargetType targetType, Long targetId, GenerationJobStatus status);
}
