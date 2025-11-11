package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogsRepository extends JpaRepository<AuditLogsEntity, Long>, JpaSpecificationExecutor<AuditLogsEntity> {
    List<AuditLogsEntity> findByTenantIdAndResult(Long tenantId, AuditResult result);
    List<AuditLogsEntity> findByActorUserId(Long actorUserId);
    List<AuditLogsEntity> findByEntityTypeAndEntityId(String entityType, Long entityId);

    // JSON field query on details
    @Query(value = "SELECT * FROM audit_logs a WHERE JSON_UNQUOTE(JSON_EXTRACT(a.details, ?1)) = ?2", nativeQuery = true)
    List<AuditLogsEntity> findByDetailsPathEquals(String jsonPath, String expectedValue);
}
