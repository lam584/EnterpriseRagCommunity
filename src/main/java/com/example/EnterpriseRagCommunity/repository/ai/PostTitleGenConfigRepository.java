package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostTitleGenConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostTitleGenConfigRepository extends JpaRepository<PostTitleGenConfigEntity, Long> {
    Optional<PostTitleGenConfigEntity> findTopByOrderByUpdatedAtDesc();
}

