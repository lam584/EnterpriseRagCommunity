package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostRiskTagGenConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostRiskTagGenConfigRepository extends JpaRepository<PostRiskTagGenConfigEntity, Long> {
    Optional<PostRiskTagGenConfigEntity> findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(String groupCode, String subType);
}
