package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.CostRecordsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CostRecordsRepository extends JpaRepository<CostRecordsEntity, Long>, JpaSpecificationExecutor<CostRecordsEntity> {

    // 按 scope（存于 JSON metadata.scope）+ model + 时间范围汇总成本
    @Query(value = """
            SELECT JSON_UNQUOTE(JSON_EXTRACT(c.metadata, '$.scope')) AS scope,
                   c.model                                         AS model,
                   SUM(c.cost)                                     AS totalCost
            FROM cost_records c
            WHERE c.occurred_at BETWEEN :start AND :end
              AND (:scope IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(c.metadata, '$.scope')) = :scope)
              AND (:model IS NULL OR c.model = :model)
            GROUP BY JSON_UNQUOTE(JSON_EXTRACT(c.metadata, '$.scope')), c.model
            """, nativeQuery = true)
    List<CostSummary> summarizeCostByScopeAndModel(@Param("scope") String scope,
                                                   @Param("model") String model,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    interface CostSummary {
        String getScope();
        String getModel();
        BigDecimal getTotalCost();
    }
}

