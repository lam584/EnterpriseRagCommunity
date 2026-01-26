package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostTagGenConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostTagGenConfigRepository extends JpaRepository<PostTagGenConfigEntity, Long> {
    Optional<PostTagGenConfigEntity> findTopByOrderByUpdatedAtDesc();
}

