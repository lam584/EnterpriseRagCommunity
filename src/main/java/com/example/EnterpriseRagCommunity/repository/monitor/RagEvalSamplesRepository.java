package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.RagEvalSamplesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RagEvalSamplesRepository extends JpaRepository<RagEvalSamplesEntity, Long>, JpaSpecificationExecutor<RagEvalSamplesEntity> {
}

