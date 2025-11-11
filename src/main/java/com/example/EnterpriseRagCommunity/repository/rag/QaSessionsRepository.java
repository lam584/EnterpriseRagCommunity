package com.example.EnterpriseRagCommunity.repository.rag;

import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface QaSessionsRepository extends JpaRepository<QaSessionsEntity, Long>, JpaSpecificationExecutor<QaSessionsEntity> {
    // ...existing code...
}

