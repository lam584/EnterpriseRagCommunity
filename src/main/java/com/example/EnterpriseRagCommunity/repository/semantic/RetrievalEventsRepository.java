package com.example.EnterpriseRagCommunity.repository.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RetrievalEventsRepository extends JpaRepository<RetrievalEventsEntity, Long>, JpaSpecificationExecutor<RetrievalEventsEntity> {
    // by user FK
    List<RetrievalEventsEntity> findByUserId(Long userId);

    // by time
    List<RetrievalEventsEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // by retrieval params
    List<RetrievalEventsEntity> findByRerankModel(String rerankModel);
}
