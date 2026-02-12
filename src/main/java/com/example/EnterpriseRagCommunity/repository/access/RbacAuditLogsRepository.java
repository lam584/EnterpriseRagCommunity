package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.RbacAuditLogsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RbacAuditLogsRepository extends JpaRepository<RbacAuditLogsEntity, Long>, JpaSpecificationExecutor<RbacAuditLogsEntity> {
    Page<RbacAuditLogsEntity> findByTargetTypeOrderByCreatedAtDesc(String targetType, Pageable pageable);
}

