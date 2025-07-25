package com.example.NewsHubCommunity.repository;

import com.example.NewsHubCommunity.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findAllByNewsId(Long newsId, Pageable pageable);
    Page<Comment> findAllByUserId(Long userId, Pageable pageable);
}
