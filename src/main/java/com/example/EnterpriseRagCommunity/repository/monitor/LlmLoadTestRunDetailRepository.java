package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.LlmLoadTestRunDetailEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LlmLoadTestRunDetailRepository extends JpaRepository<LlmLoadTestRunDetailEntity, Long> {
    Page<LlmLoadTestRunDetailEntity> findByRunIdOrderByReqIndexAsc(String runId, Pageable pageable);
    boolean existsByRunId(String runId);
    void deleteByRunId(String runId);
}
