package com.example.EnterpriseRagCommunity.repository.rag;

import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface QaMessagesRepository extends JpaRepository<QaMessagesEntity, Long>, JpaSpecificationExecutor<QaMessagesEntity> {
    // 按 session_id + role 分页查询（用于上下文加载）
    Page<QaMessagesEntity> findBySessionIdAndRoleOrderByCreatedAtAsc(Long sessionId, MessageRole role, Pageable pageable);
}
