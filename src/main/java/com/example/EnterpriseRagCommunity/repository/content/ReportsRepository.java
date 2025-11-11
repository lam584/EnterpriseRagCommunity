package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ReportsRepository extends JpaRepository<ReportsEntity, Long>, JpaSpecificationExecutor<ReportsEntity> {
    Page<ReportsEntity> findByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId, Pageable pageable);
    Page<ReportsEntity> findByStatus(ReportStatus status, Pageable pageable);
    Page<ReportsEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    Page<ReportsEntity> findByHandledById(Long handledById, Pageable pageable);
}
