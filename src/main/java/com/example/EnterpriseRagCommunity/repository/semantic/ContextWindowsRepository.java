package com.example.EnterpriseRagCommunity.repository.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.ContextWindowsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ContextWindowsRepository extends JpaRepository<ContextWindowsEntity, Long>, JpaSpecificationExecutor<ContextWindowsEntity> {
    // by event FK
    List<ContextWindowsEntity> findByEventId(Long eventId);

    // by policy
    List<ContextWindowsEntity> findByPolicy(ContextWindowPolicy policy);

    // by time
    List<ContextWindowsEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // JSON_CONTAINS query on chunk_ids JSON array
    @Query(value = "SELECT * FROM context_windows cw WHERE JSON_CONTAINS(cw.chunk_ids, CAST(:chunkId AS JSON), '$')", nativeQuery = true)
    List<ContextWindowsEntity> findByChunkIdsContains(@Param("chunkId") String chunkIdJson);

    // variant passing numeric id directly
    @Query(value = "SELECT * FROM context_windows cw WHERE JSON_CONTAINS(cw.chunk_ids, CAST(:chunkId AS JSON), '$')", nativeQuery = true)
    List<ContextWindowsEntity> findByChunkIdsContainsNumeric(@Param("chunkId") Long chunkId);
}
