package com.example.EnterpriseRagCommunity.repository.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.DocumentsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.DocumentSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentsRepository extends JpaRepository<DocumentsEntity, Long>, JpaSpecificationExecutor<DocumentsEntity> {
    // Status/active
    List<DocumentsEntity> findByIsActive(Boolean isActive);

    // Type/source
    List<DocumentsEntity> findBySourceType(DocumentSourceType sourceType);
    List<DocumentsEntity> findBySourceTypeAndTenantId(DocumentSourceType sourceType, Long tenantId);

    // Foreign keys
    List<DocumentsEntity> findByTenantId(Long tenantId);

    // Time-based
    List<DocumentsEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Lookups
    Optional<DocumentsEntity> findByIdAndIsActive(Long id, Boolean isActive);
}
