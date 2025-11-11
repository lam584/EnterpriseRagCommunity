package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.SearchLogsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchLogsRepository extends JpaRepository<SearchLogsEntity, Long>, JpaSpecificationExecutor<SearchLogsEntity> {
}

