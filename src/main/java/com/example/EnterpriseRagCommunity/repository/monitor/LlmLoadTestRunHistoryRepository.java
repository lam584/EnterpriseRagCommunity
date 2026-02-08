package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.LlmLoadTestRunHistoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LlmLoadTestRunHistoryRepository extends JpaRepository<LlmLoadTestRunHistoryEntity, String> {
    List<LlmLoadTestRunHistoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
