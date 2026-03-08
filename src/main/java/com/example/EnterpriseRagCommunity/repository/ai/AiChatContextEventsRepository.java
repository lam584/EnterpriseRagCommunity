package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.AiChatContextEventsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AiChatContextEventsRepository extends JpaRepository<AiChatContextEventsEntity, Long> {
    Page<AiChatContextEventsEntity> findByCreatedAtBetweenOrderByCreatedAtDescIdDesc(LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<AiChatContextEventsEntity> findByCreatedAtAfterOrderByCreatedAtDescIdDesc(LocalDateTime from, Pageable pageable);

    Page<AiChatContextEventsEntity> findByCreatedAtBeforeOrderByCreatedAtDescIdDesc(LocalDateTime to, Pageable pageable);

    Page<AiChatContextEventsEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
