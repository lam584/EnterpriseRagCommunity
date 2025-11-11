package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.PostTagEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostTagSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PostTagRepository extends JpaRepository<PostTagEntity, PostTagEntity.PostTagKey>, JpaSpecificationExecutor<PostTagEntity> {
    Page<PostTagEntity> findByPostId(Long postId, Pageable pageable);
    Page<PostTagEntity> findByTagId(Long tagId, Pageable pageable);
    Page<PostTagEntity> findBySource(PostTagSource source, Pageable pageable);
}

