package com.example.EnterpriseRagCommunity.repository.rag;

import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QaTurnsRepository extends JpaRepository<QaTurnsEntity, Long>, JpaSpecificationExecutor<QaTurnsEntity> {
    // 按 sessionId 查询轮次列表（基于字段名 sessionId）
    List<QaTurnsEntity> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    @org.springframework.data.jpa.repository.Modifying
    void deleteBySessionId(Long sessionId);
}
