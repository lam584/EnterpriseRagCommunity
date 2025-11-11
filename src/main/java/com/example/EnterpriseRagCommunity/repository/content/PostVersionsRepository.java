package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.PostVersionsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostVersionsRepository extends JpaRepository<PostVersionsEntity, Long>, JpaSpecificationExecutor<PostVersionsEntity> {
    List<PostVersionsEntity> findByPostIdOrderByVersionDesc(Long postId);
    Page<PostVersionsEntity> findByPostId(Long postId, Pageable pageable);
}
