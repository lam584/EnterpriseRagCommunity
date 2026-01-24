package com.example.EnterpriseRagCommunity.repository.rag;

import com.example.EnterpriseRagCommunity.entity.rag.QaMessageSourcesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QaMessageSourcesRepository extends JpaRepository<QaMessageSourcesEntity, Long> {
    List<QaMessageSourcesEntity> findByMessageIdInOrderByMessageIdAscSourceIndexAsc(List<Long> messageIds);
}
