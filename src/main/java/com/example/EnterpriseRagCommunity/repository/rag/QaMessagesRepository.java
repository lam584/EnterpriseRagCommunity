package com.example.EnterpriseRagCommunity.repository.rag;

import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QaMessagesRepository extends JpaRepository<QaMessagesEntity, Long>, JpaSpecificationExecutor<QaMessagesEntity> {
    // 按 session_id + role 分页查询（用于上下文加载）
    Page<QaMessagesEntity> findBySessionIdAndRoleOrderByCreatedAtAsc(Long sessionId, MessageRole role, Pageable pageable);

    List<QaMessagesEntity> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    @org.springframework.data.jpa.repository.Modifying
    void deleteBySessionId(Long sessionId);

    @Query(value = "SELECT m.* FROM qa_messages m JOIN qa_sessions s ON s.id = m.session_id WHERE s.user_id = :userId AND s.is_active = 1 AND MATCH(m.content) AGAINST(:q IN BOOLEAN MODE)", nativeQuery = true)
    Page<QaMessagesEntity> searchMyMessagesFulltext(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);
}
