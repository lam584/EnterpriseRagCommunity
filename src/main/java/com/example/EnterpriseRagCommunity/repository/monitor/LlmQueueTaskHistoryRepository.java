package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.LlmQueueTaskHistoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LlmQueueTaskHistoryRepository extends JpaRepository<LlmQueueTaskHistoryEntity, String> {
    List<LlmQueueTaskHistoryEntity> findByFinishedAtIsNotNullOrderByFinishedAtDesc(Pageable pageable);

    long deleteByFinishedAtBefore(LocalDateTime cutoff);
}
