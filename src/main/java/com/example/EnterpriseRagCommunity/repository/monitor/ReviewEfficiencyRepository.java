package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.ReviewEfficiencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewEfficiencyRepository extends JpaRepository<ReviewEfficiencyEntity, Long>, JpaSpecificationExecutor<ReviewEfficiencyEntity> {
}

