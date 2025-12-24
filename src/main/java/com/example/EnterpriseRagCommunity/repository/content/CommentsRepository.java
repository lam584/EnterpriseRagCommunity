package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface CommentsRepository extends JpaRepository<CommentsEntity, Long>, JpaSpecificationExecutor<CommentsEntity> {
    Page<CommentsEntity> findByPostIdAndStatusAndIsDeletedFalse(Long postId, CommentStatus status, Pageable pageable);
    Page<CommentsEntity> findByAuthorIdAndIsDeletedFalse(Long authorId, Pageable pageable);
    Page<CommentsEntity> findByParentIdAndIsDeletedFalse(Long parentId, Pageable pageable);
    Page<CommentsEntity> findByIsDeletedFalseAndCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    long countByPostIdAndStatusAndIsDeletedFalse(Long postId, CommentStatus status);
}
