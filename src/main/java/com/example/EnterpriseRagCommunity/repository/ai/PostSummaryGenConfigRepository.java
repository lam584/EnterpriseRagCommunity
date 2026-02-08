package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostSummaryGenConfigRepository extends JpaRepository<PostSummaryGenConfigEntity, Long> {
    Optional<PostSummaryGenConfigEntity> findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(String groupCode, String subType);
}
