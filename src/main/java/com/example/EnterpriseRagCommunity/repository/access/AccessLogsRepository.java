package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.AccessLogsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AccessLogsRepository extends JpaRepository<AccessLogsEntity, Long>, JpaSpecificationExecutor<AccessLogsEntity> {
    List<AccessLogsEntity> findTop1000ByArchivedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(LocalDateTime createdAt);
}
