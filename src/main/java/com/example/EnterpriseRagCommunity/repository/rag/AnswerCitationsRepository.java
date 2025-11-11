package com.example.EnterpriseRagCommunity.repository.rag;

import com.example.EnterpriseRagCommunity.entity.rag.AnswerCitationsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnswerCitationsRepository extends JpaRepository<AnswerCitationsEntity, Long>, JpaSpecificationExecutor<AnswerCitationsEntity> {
    // 按 messageId 查询所有引用片段
    List<AnswerCitationsEntity> findByMessageId(Long messageId);
}
