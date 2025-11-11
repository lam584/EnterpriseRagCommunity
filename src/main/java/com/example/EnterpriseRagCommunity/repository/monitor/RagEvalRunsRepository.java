package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.RagEvalRunsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RagEvalRunsRepository extends JpaRepository<RagEvalRunsEntity, Long>, JpaSpecificationExecutor<RagEvalRunsEntity> {
}

