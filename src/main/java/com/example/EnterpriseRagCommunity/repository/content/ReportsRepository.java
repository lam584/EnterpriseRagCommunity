package com.example.EnterpriseRagCommunity.repository.content;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;

@Repository
public interface ReportsRepository extends JpaRepository<ReportsEntity, Long>, JpaSpecificationExecutor<ReportsEntity> {
    Page<ReportsEntity> findByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId, Pageable pageable);
    List<ReportsEntity> findAllByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, Long targetId, ReportStatus status);
        List<ReportsEntity> findAllByTargetTypeAndTargetIdAndCreatedAtAfter(ReportTargetType targetType, Long targetId, LocalDateTime after);
    long countByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId);
    long countByTargetTypeAndTargetIdAndCreatedAtAfter(ReportTargetType targetType, Long targetId, LocalDateTime after);
    @Query("select count(distinct r.reporterId) from ReportsEntity r where r.targetType = :targetType and r.targetId = :targetId and r.createdAt >= :after")
    long countDistinctReporterIdByTargetTypeAndTargetIdAndCreatedAtAfter(@Param("targetType") ReportTargetType targetType,
                                                                         @Param("targetId") Long targetId,
                                                                         @Param("after") LocalDateTime after);
    Page<ReportsEntity> findByStatus(ReportStatus status, Pageable pageable);
    Page<ReportsEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    Page<ReportsEntity> findByHandledById(Long handledById, Pageable pageable);

    @Modifying
    @Query("update ReportsEntity r set r.status = :newStatus, r.handledById = :handledById, r.handledAt = :handledAt, r.resolution = :resolution " +
            "where r.targetType = :targetType and r.targetId = :targetId and r.status = com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus.PENDING")
    int resolveAllPendingByTarget(@Param("targetType") ReportTargetType targetType,
                                  @Param("targetId") Long targetId,
                                  @Param("newStatus") ReportStatus newStatus,
                                  @Param("handledById") Long handledById,
                                  @Param("handledAt") LocalDateTime handledAt,
                                  @Param("resolution") String resolution);
}
