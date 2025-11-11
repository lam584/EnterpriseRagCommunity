package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.SystemEventsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemEventsRepository extends JpaRepository<SystemEventsEntity, Long>, JpaSpecificationExecutor<SystemEventsEntity> {
}

