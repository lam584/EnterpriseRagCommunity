package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LlmRoutingPolicyRepository extends JpaRepository<LlmRoutingPolicyEntity, LlmRoutingPolicyId> {
    List<LlmRoutingPolicyEntity> findByIdEnvOrderByIdTaskTypeAsc(String env);

    List<LlmRoutingPolicyEntity> findByIdEnvOrderBySortIndexAscIdTaskTypeAsc(String env);
}
