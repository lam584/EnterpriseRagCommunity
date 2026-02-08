package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SemanticTranslateConfigRepository extends JpaRepository<SemanticTranslateConfigEntity, Long> {
    Optional<SemanticTranslateConfigEntity> findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(String groupCode, String subType);
}
