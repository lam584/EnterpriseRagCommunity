package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PostAttachmentsRepository extends JpaRepository<PostAttachmentsEntity, Long>, JpaSpecificationExecutor<PostAttachmentsEntity> {
    Page<PostAttachmentsEntity> findByPostId(Long postId, Pageable pageable);

    void deleteByPostId(Long postId);
}
