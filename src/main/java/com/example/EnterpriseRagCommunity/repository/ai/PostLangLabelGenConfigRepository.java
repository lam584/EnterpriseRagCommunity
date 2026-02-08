package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostLangLabelGenConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostLangLabelGenConfigRepository extends JpaRepository<PostLangLabelGenConfigEntity, Long> {
    Optional<PostLangLabelGenConfigEntity> findTopByGroupCodeAndSubTypeOrderByUpdatedAtDesc(String groupCode, String subType);
}
