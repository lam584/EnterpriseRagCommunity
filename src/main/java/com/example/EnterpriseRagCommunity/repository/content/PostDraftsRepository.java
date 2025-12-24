package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.PostDraftsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostDraftsRepository extends JpaRepository<PostDraftsEntity, Long>, JpaSpecificationExecutor<PostDraftsEntity> {
    Page<PostDraftsEntity> findByAuthorIdOrderByUpdatedAtDesc(Long authorId, Pageable pageable);

    Optional<PostDraftsEntity> findByIdAndAuthorId(Long id, Long authorId);
}

