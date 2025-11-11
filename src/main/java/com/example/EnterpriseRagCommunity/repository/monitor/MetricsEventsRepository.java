package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.MetricsEventsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MetricsEventsRepository extends JpaRepository<MetricsEventsEntity, Long>, JpaSpecificationExecutor<MetricsEventsEntity> {
    // 按 name + 时间范围的聚合统计
    @Query(value = """
            SELECT m.name AS name,
                   COUNT(*) AS count,
                   AVG(m.value) AS avgValue,
                   SUM(m.value) AS sumValue
            FROM metrics_events m
            WHERE m.name = :name
              AND m.ts BETWEEN :start AND :end
            GROUP BY m.name
            """, nativeQuery = true)
    List<MetricsSummary> aggregateByNameAndTsBetween(@Param("name") String name,
                                                     @Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    interface MetricsSummary {
        String getName();
        Long getCount();
        Double getAvgValue();
        Double getSumValue();
    }
}
