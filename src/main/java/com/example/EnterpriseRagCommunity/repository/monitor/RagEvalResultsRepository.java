package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.RagEvalResultsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RagEvalResultsRepository extends JpaRepository<RagEvalResultsEntity, Long>, JpaSpecificationExecutor<RagEvalResultsEntity> {

    // 按 run_id 分组统计平均 EM / F1 / Latency（直接使用列 em/f1/latency_ms）
    @Query(value = """
            SELECT r.run_id AS runId,
                   AVG(r.em)                         AS avgEm,
                   AVG(r.f1)                         AS avgF1,
                   AVG(r.latency_ms)                 AS avgLatency
            FROM rag_eval_results r
            GROUP BY r.run_id
            """, nativeQuery = true)
    List<RunEvalAverages> averageMetricsByRun();

    // 单个 run 的平均指标
    @Query(value = """
            SELECT r.run_id AS runId,
                   AVG(r.em)                         AS avgEm,
                   AVG(r.f1)                         AS avgF1,
                   AVG(r.latency_ms)                 AS avgLatency
            FROM rag_eval_results r
            WHERE r.run_id = :runId
            GROUP BY r.run_id
            """, nativeQuery = true)
    RunEvalAverages averageMetricsForRun(@Param("runId") Long runId);

    interface RunEvalAverages {
        Long getRunId();
        Double getAvgEm();
        Double getAvgF1();
        Double getAvgLatency();
    }
}
